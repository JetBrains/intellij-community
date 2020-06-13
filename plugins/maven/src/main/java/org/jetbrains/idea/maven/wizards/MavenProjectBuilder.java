// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.DeprecatedProjectBuilderForImport;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction;
import org.jetbrains.idea.maven.utils.*;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static icons.OpenapiIcons.RepositoryLibraryLogo;

/**
 * Do not use this project import builder directly.
 * <p>
 * Internal stable Api
 * Use {@link com.intellij.ide.actions.ImportModuleAction#doImport} to import (attach) a new project.
 * Use {@link com.intellij.ide.impl.ProjectUtil#openOrImport} to open (import) a new project.
 */
public final class MavenProjectBuilder extends ProjectImportBuilder<MavenProject> implements DeprecatedProjectBuilderForImport {
  private static final Logger LOG = Logger.getInstance(MavenProjectBuilder.class);

  private static class Parameters {
    private Project myProjectToUpdate;

    private MavenGeneralSettings myGeneralSettingsCache;
    private MavenImportingSettings myImportingSettingsCache;

    private Path myImportRoot;
    private List<VirtualFile> myFiles;

    private MavenProjectsTree myMavenProjectTree;
    private List<MavenProject> mySelectedProjects;

    private boolean myOpenModulesConfigurator;
  }

  private Parameters myParameters;

  @Override
  @NotNull
  public String getName() {
    return MavenProjectBundle.message("maven.name");
  }

  @Override
  public Icon getIcon() {
    return RepositoryLibraryLogo;
  }

  @Override
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

  private boolean setupProjectImport(@NotNull Project project) {
    Path rootDirectory = getRootPath();
    return rootDirectory != null && setRootDirectory(project, rootDirectory) && selectProjectsToUpdate();
  }

  private boolean selectProjectsToUpdate() {
    Parameters parameters = getParameters();
    MavenProjectsTree projectsTree = parameters.myMavenProjectTree;
    List<MavenProject> projects = projectsTree.getRootProjects();
    if (projects.isEmpty()) return false;
    parameters.mySelectedProjects = projects;
    return true;
  }

  private void setupProjectName(@NotNull Project project) {
    if (!(project instanceof ProjectEx)) {
      return;
    }

    String projectName = getSuggestedProjectName();
    if (projectName != null) {
      ((ProjectEx)project).setProjectName(projectName);
    }
  }

  @Nullable
  public Sdk suggestProjectSdk() {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    ProjectRootManager defaultProjectManager = ProjectRootManager.getInstance(defaultProject);
    Sdk defaultProjectSdk = defaultProjectManager.getProjectSdk();
    if (defaultProjectSdk != null) return null;
    ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    SdkType sdkType = ExternalSystemJdkUtil.getJavaSdkType();
    return projectJdkTable.getSdksOfType(sdkType).stream()
      .filter(it -> it.getHomePath() != null && JdkUtil.checkForJre(it.getHomePath()))
      .max(sdkType.versionComparator())
      .orElse(null);
  }

  private void setupProjectSdk(@NotNull Project project) {
    if (ProjectRootManager.getInstance(project).getProjectSdk() == null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        Sdk projectSdk = suggestProjectSdk();
        if (projectSdk == null) return;
        NewProjectUtil.applyJdkToProject(project, projectSdk);
      });
    }
  }

  @Override
  public List<Module> commit(Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {
    if (!setupProjectImport(project)) {
      LOG.debug(String.format("Cannot import project for %s", project.toString()));
      return Collections.emptyList();
    }
    setupProjectName(project);
    setupProjectSdk(project);

    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();

    settings.generalSettings = getGeneralSettings();
    settings.importingSettings = getImportingSettings();

    String settingsFile = System.getProperty("idea.maven.import.settings.file");
    if (!StringUtil.isEmptyOrSpaces(settingsFile)) {
      settings.generalSettings.setUserSettingsFile(settingsFile.trim());
    }

    MavenExplicitProfiles selectedProfiles = MavenExplicitProfiles.NONE.clone();

    String enabledProfilesList = System.getProperty("idea.maven.import.enabled.profiles");
    String disabledProfilesList = System.getProperty("idea.maven.import.disabled.profiles");
    if (enabledProfilesList != null || disabledProfilesList != null) {
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
      try {
        return promise.blockingGet(0);
      }
      catch (TimeoutException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

    boolean isFromUI = model != null;
    if (isFromUI) {
      return manager.importProjects(new IdeUIModifiableModelsProvider(project, model, (ModulesConfigurator)modulesProvider, artifactModel));
    }
    return manager.importProjects();
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

  /**
   * @deprecated Use {@link #setRootDirectory(Project, Path)}
   */
  @Deprecated
  public boolean setRootDirectory(@Nullable Project projectToUpdate, @NotNull String root) {
    return setRootDirectory(projectToUpdate, Paths.get(root));
  }

  public boolean setRootDirectory(@Nullable Project projectToUpdate, @NotNull Path root) {
    getParameters().myFiles = null;
    getParameters().myMavenProjectTree = null;

    // We cannot determinate project in non-EDT thread.
    getParameters().myProjectToUpdate = projectToUpdate != null ? projectToUpdate : ProjectManager.getInstance().getDefaultProject();

    return runConfigurationProcess(MavenProjectBundle.message("maven.scanning.projects"), new MavenTask() {
      @Override
      public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
        indicator.setText(MavenProjectBundle.message("maven.locating.files"));

        getParameters().myImportRoot = root;
        if (getParameters().myImportRoot == null) {
          throw new MavenProcessCanceledException();
        }

        Path file = getRootPath();
        VirtualFile virtualFile = file == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()));
        if (virtualFile == null) {
          throw new MavenProcessCanceledException();
        }
        getParameters().myFiles = FileFinder.findPomFiles(virtualFile.getChildren(),
          LookForNestedToggleAction.isSelected(),
          indicator,
          new ArrayList<>());

        readMavenProjectTree(indicator);

        indicator.setText("");
        indicator.setText2("");
      }
    });
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public boolean setSelectedProfiles(MavenExplicitProfiles profiles) {
    return runConfigurationProcess(MavenProjectBundle.message("maven.scanning.projects"), new MavenTask() {
      @Override
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
    tree.addManagedFilesWithProfiles(getParameters().myFiles, MavenExplicitProfiles.NONE);
    tree.updateAll(false, getGeneralSettings(), process);

    getParameters().myMavenProjectTree = tree;
    getParameters().mySelectedProjects = tree.getRootProjects();
  }

  @Override
  public List<MavenProject> getList() {
    return getParameters().myMavenProjectTree.getRootProjects();
  }

  @Override
  public void setList(List<MavenProject> projects) {
    getParameters().mySelectedProjects = projects;
  }

  @Override
  public boolean isMarked(MavenProject element) {
    return getParameters().mySelectedProjects.contains(element);
  }

  @Override
  public boolean isOpenProjectSettingsAfter() {
    return getParameters().myOpenModulesConfigurator;
  }

  @Override
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

  /**
   * @deprecated Use {@link #getRootPath()}
   */
  @Deprecated
  public @Nullable VirtualFile getRootDirectory() {
    Path rootPath = getRootPath();
    return rootPath == null ? null : VfsUtil.findFile(rootPath, false);
  }

  public @Nullable Path getRootPath() {
    if (getParameters().myImportRoot == null && isUpdate()) {
      Project project = getProjectToUpdate();
      getParameters().myImportRoot = project == null ? null : Paths.get(Objects.requireNonNull(project.getBasePath()));
    }
    return getParameters().myImportRoot;
  }

  public @Nullable String getSuggestedProjectName() {
    List<MavenProject> list = getParameters().myMavenProjectTree.getRootProjects();
    return list.size() == 1 ? list.get(0).getMavenId().getArtifactId() : null;
  }

  @Override
  public void setFileToImport(@NotNull String path) {
    setFileToImport(Paths.get(path));
  }

  public void setFileToImport(@NotNull Path path) {
    getParameters().myImportRoot = Files.isDirectory(path) ? path : path.getParent();
  }

  @Nullable
  @Override
  public Project createProject(String name, String path) {
    return ExternalProjectsManagerImpl.setupCreatedProject(super.createProject(name, path));
  }

  @NotNull
  @Override
  public ProjectOpenProcessor getProjectOpenProcessor() {
    return ProjectOpenProcessor.EXTENSION_POINT_NAME.findExtensionOrFail(MavenProjectOpenProcessor.class);
  }
}
