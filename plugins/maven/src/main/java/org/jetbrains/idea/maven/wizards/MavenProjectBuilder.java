// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.*;

import javax.swing.*;
import java.util.*;

public class MavenProjectBuilder extends ProjectImportBuilder<MavenProject> {
  private static class Parameters {
    private Project myProjectToUpdate;

    private MavenGeneralSettings myGeneralSettingsCache;
    private MavenImportingSettings myImportingSettingsCache;

    private VirtualFile myImportRoot;
    private List<VirtualFile> myFiles;
    private List<String> myProfiles = new ArrayList<>();
    private List<String> myActivatedProfiles = new ArrayList<>();
    private MavenExplicitProfiles mySelectedProfiles = MavenExplicitProfiles.NONE;

    private MavenProjectsTree myMavenProjectTree;
    private List<MavenProject> mySelectedProjects;

    private boolean myOpenModulesConfigurator;
  }

  private Parameters myParameters;

  @NotNull
  public String getName() {
    return ProjectBundle.message("maven.name");
  }

  public Icon getIcon() {
    return MavenIcons.MavenLogo;
  }

  public void cleanup() {
    myParameters = null;
    super.cleanup();
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == JavaSdk.getInstance();
  }

  private Parameters getParameters() {
    if (myParameters == null) {
      myParameters = new Parameters();
    }
    return myParameters;
  }

  @Override
  public boolean validate(Project current, Project dest) {
    return true;
  }

  @Override
  public List<Module> commit(Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();

    settings.generalSettings = getGeneralSettings();
    settings.importingSettings = getImportingSettings();

    String settingsFile = System.getProperty("idea.maven.import.settings.file");
    if (!StringUtil.isEmptyOrSpaces(settingsFile)) {
      settings.generalSettings.setUserSettingsFile(settingsFile.trim());
    }

    MavenExplicitProfiles selectedProfiles = getSelectedProfiles();

    String enabledProfilesList = System.getProperty("idea.maven.import.enabled.profiles");
    String disabledProfilesList = System.getProperty("idea.maven.import.disabled.profiles");
    if (enabledProfilesList != null || disabledProfilesList != null) {
      selectedProfiles = selectedProfiles.clone();
      appendProfilesFromString(selectedProfiles.getEnabledProfiles(), enabledProfilesList);
      appendProfilesFromString(selectedProfiles.getDisabledProfiles(), disabledProfilesList);
    }

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

    manager.setIgnoredState(getParameters().mySelectedProjects, false);

    manager.addManagedFilesWithProfiles(MavenUtil.collectFiles(getParameters().mySelectedProjects), selectedProfiles);
    manager.waitForReadingCompletion();

    if (ApplicationManager.getApplication().isHeadlessEnvironment() &&
        !ApplicationManager.getApplication().isUnitTestMode()) {
      Promise<List<Module>> promise = manager.scheduleImportAndResolve();
      manager.waitForResolvingCompletion();
      return promise.blockingGet(0);
    }

    boolean isFromUI = model != null;
    return manager.importProjects(isFromUI
                                  ? new IdeUIModifiableModelsProvider(project, model, (ModulesConfigurator)modulesProvider, artifactModel)
                                  : new IdeModifiableModelsProviderImpl(project));
  }

  private static void appendProfilesFromString(Collection<String> selectedProfiles, String profilesList) {
    if (profilesList == null) return;

    for (String profile : StringUtil.split(profilesList, ",")) {
      String trimmedProfileName = profile.trim();
      if (!trimmedProfileName.isEmpty()) {
        selectedProfiles.add(trimmedProfileName);
      }
    }
  }

  public boolean setRootDirectory(@Nullable Project projectToUpdate, final String root) {
    getParameters().myFiles = null;
    getParameters().myProfiles.clear();
    getParameters().myActivatedProfiles.clear();
    getParameters().myMavenProjectTree = null;

    // We cannot determinate project in non-EDT thread.
    getParameters().myProjectToUpdate = projectToUpdate != null ? projectToUpdate : ProjectManager.getInstance().getDefaultProject();

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenTask() {
      public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
        indicator.setText(ProjectBundle.message("maven.locating.files"));

        getParameters().myImportRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
        if (getParameters().myImportRoot == null) throw new MavenProcessCanceledException();

        final VirtualFile file = getRootDirectory();
        if (file == null) throw new MavenProcessCanceledException();

        getParameters().myFiles = FileFinder.findPomFiles(file.getChildren(),
                                                          getImportingSettings().isLookForNested(),
                                                          indicator,
                                                          new ArrayList<>());

        collectProfiles(indicator);
        if (getParameters().myProfiles.isEmpty()) {
          readMavenProjectTree(indicator);
        }

        indicator.setText("");
        indicator.setText2("");
      }
    });
  }

  private void collectProfiles(MavenProgressIndicator process) {
    process.setText(ProjectBundle.message("maven.searching.profiles"));

    Set<String> availableProfiles = new LinkedHashSet<>();
    Set<String> activatedProfiles = new LinkedHashSet<>();
    MavenProjectReader reader = new MavenProjectReader(getProjectToUpdate());
    MavenGeneralSettings generalSettings = getGeneralSettings();
    MavenProjectReaderProjectLocator locator = new MavenProjectReaderProjectLocator() {
      public VirtualFile findProjectFile(MavenId coordinates) {
        return null;
      }
    };
    for (VirtualFile f : getParameters().myFiles) {
      MavenProject project = new MavenProject(f);
      process.setText2(ProjectBundle.message("maven.reading.pom", f.getPath()));
      project.read(generalSettings, MavenExplicitProfiles.NONE, reader, locator);
      availableProfiles.addAll(project.getProfilesIds());
      activatedProfiles.addAll(project.getActivatedProfilesIds().getEnabledProfiles());
    }
    getParameters().myProfiles = new ArrayList<>(availableProfiles);
    getParameters().myActivatedProfiles = new ArrayList<>(activatedProfiles);
  }

  public List<String> getProfiles() {
    return getParameters().myProfiles;
  }

  public List<String> getActivatedProfiles() {
    return getParameters().myActivatedProfiles;
  }

  public MavenExplicitProfiles getSelectedProfiles() {
    return getParameters().mySelectedProfiles;
  }

  public boolean setSelectedProfiles(MavenExplicitProfiles profiles) {
    getParameters().myMavenProjectTree = null;
    getParameters().mySelectedProfiles = profiles;

    // We cannot determinate project in non-EDT thread.
    getParameters().myProjectToUpdate = getProjectOrDefault();
    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenTask() {
      public void run(MavenProgressIndicator indicator) {
        readMavenProjectTree(indicator);
        indicator.setText2("");
      }
    });
  }

  private static boolean runConfigurationProcess(String message, MavenTask p) {
    try {
      MavenUtil.run(null, message, p);
    }
    catch (MavenProcessCanceledException e) {
      return false;
    }
    return true;
  }

  private void readMavenProjectTree(MavenProgressIndicator process) {
    MavenProjectsTree tree = new MavenProjectsTree(getProjectOrDefault());
    tree.addManagedFilesWithProfiles(getParameters().myFiles, getParameters().mySelectedProfiles);
    tree.updateAll(false, getGeneralSettings(), process);

    getParameters().myMavenProjectTree = tree;
    getParameters().mySelectedProjects = tree.getRootProjects();
  }

  public List<MavenProject> getList() {
    return getParameters().myMavenProjectTree.getRootProjects();
  }

  public void setList(List<MavenProject> projects) {
    getParameters().mySelectedProjects = projects;
  }

  public boolean isMarked(MavenProject element) {
    return getParameters().mySelectedProjects.contains(element);
  }

  public boolean isOpenProjectSettingsAfter() {
    return getParameters().myOpenModulesConfigurator;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    getParameters().myOpenModulesConfigurator = on;
  }

  public MavenGeneralSettings getGeneralSettings() {
    if (getParameters().myGeneralSettingsCache == null) {
      ApplicationManager.getApplication().runReadAction(() -> {
        getParameters().myGeneralSettingsCache = getDirectProjectsSettings().generalSettings.clone();
      });
    }
    return getParameters().myGeneralSettingsCache;
  }

  public MavenImportingSettings getImportingSettings() {
    if (getParameters().myImportingSettingsCache == null) {
      ApplicationManager.getApplication().runReadAction(() -> {
        getParameters().myImportingSettingsCache = getDirectProjectsSettings().importingSettings.clone();
      });
    }
    return getParameters().myImportingSettingsCache;
  }

  private MavenWorkspaceSettings getDirectProjectsSettings() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Project project = isUpdate() ? getProjectToUpdate() : null;
    if (project == null || project.isDisposed()) project = ProjectManager.getInstance().getDefaultProject();

    return MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
  }

  public void setFiles(List<VirtualFile> files) {
    getParameters().myFiles = files;
  }

  @Nullable
  public Project getProjectToUpdate() {
    if (getParameters().myProjectToUpdate == null) {
      getParameters().myProjectToUpdate = getCurrentProject();
    }
    return getParameters().myProjectToUpdate;
  }

  @NotNull
  public Project getProjectOrDefault() {
    Project project = getProjectToUpdate();
    if (project == null || project.isDisposed()) project = ProjectManager.getInstance().getDefaultProject();
    return project;
  }

  @Nullable
  public VirtualFile getRootDirectory() {
    if (getParameters().myImportRoot == null && isUpdate()) {
      final Project project = getProjectToUpdate();
      getParameters().myImportRoot = project != null ? project.getBaseDir() : null;
    }
    return getParameters().myImportRoot;
  }

  public String getSuggestedProjectName() {
    final List<MavenProject> list = getParameters().myMavenProjectTree.getRootProjects();
    if (list.size() == 1) {
      return list.get(0).getMavenId().getArtifactId();
    }
    return null;
  }

  @Override
  public void setFileToImport(String path) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    getParameters().myImportRoot = file == null || file.isDirectory() ? file : file.getParent();
  }

  @Nullable
  @Override
  public Project createProject(String name, String path) {
    return ExternalProjectsManagerImpl.setupCreatedProject(super.createProject(name, path));
  }
}
