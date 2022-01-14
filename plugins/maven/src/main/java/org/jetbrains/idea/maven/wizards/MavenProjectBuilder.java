// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.ExternalStorageConfigurationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.DeprecatedProjectBuilderForImport;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.importing.MavenModuleNameMapper;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction;
import org.jetbrains.idea.maven.project.importing.MavenImportingManager;
import org.jetbrains.idea.maven.project.importing.RootPath;
import org.jetbrains.idea.maven.server.MavenWrapperSupport;
import org.jetbrains.idea.maven.utils.*;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static icons.OpenapiIcons.RepositoryLibraryLogo;
import static org.jetbrains.idea.maven.server.MavenServerManager.WRAPPED_MAVEN;

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

    private Path myImportRootDirectory;
    private VirtualFile myImportProjectFile;
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


  @Override
  public List<Module> commit(Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {
    boolean isVeryNewProject = project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == Boolean.TRUE;
    if (isVeryNewProject) {
      ExternalStorageConfigurationManager.getInstance(project).setEnabled(true);
    }

    if (ApplicationManager.getApplication().isDispatchThread()) {
      FileDocumentManager.getInstance().saveAllDocuments();
    }

    MavenUtil.setupProjectSdk(project);


    if (Registry.is("maven.new.import")) {
      Module dummyModule = createDummyModule(project);
      VirtualFile rootPath = LocalFileSystem.getInstance().findFileByNioFile(getRootPath());
      MavenImportingManager.getInstance(project).openProjectAndImport(
        new RootPath(rootPath),
        getImportingSettings(),
        getGeneralSettings()
      );
      return Collections.singletonList(dummyModule);
    }

    if (!setupProjectImport(project)) {
      LOG.debug(String.format("Cannot import project for %s", project.toString()));
      return Collections.emptyList();
    }

    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();

    settings.setGeneralSettings(getGeneralSettings());
    settings.setImportingSettings(getImportingSettings());

    String settingsFile = System.getProperty("idea.maven.import.settings.file");
    if (!StringUtil.isEmptyOrSpaces(settingsFile)) {
      settings.getGeneralSettings().setUserSettingsFile(settingsFile.trim());
    }

    MavenProjectsNavigator projectsNavigator = MavenProjectsNavigator.getInstance(project);
    if (projectsNavigator != null) projectsNavigator.setGroupModules(true);

    String distributionUrl = MavenWrapperSupport.getWrapperDistributionUrl(ProjectUtil.guessProjectDir(project));
    if (distributionUrl != null) {
      settings.getGeneralSettings().setMavenHome(WRAPPED_MAVEN);
    }

    MavenExplicitProfiles selectedProfiles = MavenExplicitProfiles.NONE.clone();

    String enabledProfilesList = System.getProperty("idea.maven.import.enabled.profiles");
    String disabledProfilesList = System.getProperty("idea.maven.import.disabled.profiles");
    if (enabledProfilesList != null || disabledProfilesList != null) {
      appendProfilesFromString(selectedProfiles.getEnabledProfiles(), enabledProfilesList);
      appendProfilesFromString(selectedProfiles.getDisabledProfiles(), disabledProfilesList);
    }


    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment() &&
        !manager.hasProjects() && settings.generalSettings.isShowDialogWithAdvancedSettings()) {
      showGeneralSettingsConfigurationDialog(project, settings.generalSettings);
    }

    manager.setIgnoredState(getParameters().mySelectedProjects, false);


    if (isVeryNewProject && Registry.is("maven.create.dummy.module.on.first.import")) {
      Module dummyModule = createDummyModule(project);
      manager.addManagedFilesWithProfiles(MavenUtil.collectFiles(getParameters().mySelectedProjects), selectedProfiles, dummyModule);
      return Collections.singletonList(dummyModule);
    }
    else {
      manager.addManagedFilesWithProfiles(MavenUtil.collectFiles(getParameters().mySelectedProjects), selectedProfiles, null);
    }

    manager.waitForReadingCompletion();
    //noinspection UnresolvedPluginConfigReference
    if (ApplicationManager.getApplication().isHeadlessEnvironment() &&
        !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode() &&
        (!MavenUtil.isMavenUnitTestModeEnabled() ||
         Registry.is("ide.force.maven.import", false)) // workaround for inspection integration test
    ) {
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

  private @Nullable Module createDummyModule(Project project) {
    if (ModuleManager.getInstance(project).getModules().length == 0) {
      MavenProject root = ContainerUtil.getFirstItem(getParameters().mySelectedProjects);
      if (root == null) return null;
      VirtualFile contentRoot = root.getDirectoryFile();

      return WriteAction.compute(() -> {
        Module module = ModuleManager.getInstance(project)
          .newModule(contentRoot.toNioPath(), ModuleTypeManager.getInstance().getDefaultModuleType().getId());
        ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
        modifiableModel.addContentEntry(contentRoot);
        modifiableModel.commit();
        renameModuleToProjectName(project, module, root);
        if (MavenProjectImporter.isImportToWorkspaceModelEnabled() || MavenProjectImporter.isImportToTreeStructureEnabled()) {
          //this is needed to ensure that dummy module created here will be correctly replaced by real ModuleEntity when import finishes
          ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true);
        }
        return module;
      });
    }
    return null;
  }

  private static void renameModuleToProjectName(Project project, Module module, MavenProject root) {
    try {
      ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
      moduleModel.renameModule(module, MavenModuleNameMapper.resolveModuleName(root));
      moduleModel.commit();
    }
    catch (ModuleWithNameAlreadyExists ignore) {
    }
  }

  private static void showGeneralSettingsConfigurationDialog(@NotNull Project project, @NotNull MavenGeneralSettings generalSettings) {
    MavenEnvironmentSettingsDialog dialog = new MavenEnvironmentSettingsDialog(project, generalSettings);
    ApplicationManager.getApplication().invokeAndWait(dialog::show);
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
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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

        getParameters().myImportRootDirectory = root;
        if (getParameters().myImportRootDirectory == null) {
          throw new MavenProcessCanceledException();
        }

        getParameters().myFiles = getProjectFiles(indicator);
        readMavenProjectTree(indicator);

        indicator.setText("");
        indicator.setText2("");
      }
    });
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public boolean setSelectedProfiles(MavenExplicitProfiles profiles) {
    return runConfigurationProcess(MavenProjectBundle.message("maven.scanning.projects"), new MavenTask() {
      @Override
      public void run(MavenProgressIndicator indicator) {
        readMavenProjectTree(indicator);
        indicator.setText2("");
      }
    });
  }

  private static boolean runConfigurationProcess(@NlsContexts.DialogTitle String message, MavenTask p) {
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

  private MavenGeneralSettings getGeneralSettings() {
    if (getParameters().myGeneralSettingsCache == null) {
      ApplicationManager.getApplication().runReadAction(() -> {
        getParameters().myGeneralSettingsCache = getDirectProjectsSettings().getGeneralSettings().clone();
        getParameters().myGeneralSettingsCache.setUseMavenConfig(true);
        List<VirtualFile> rootFiles = getParameters().myFiles;
        if(rootFiles == null) {
          rootFiles = Collections.singletonList(LocalFileSystem.getInstance().findFileByNioFile(getRootPath()));
        }
        getParameters().myGeneralSettingsCache.updateFromMavenConfig(rootFiles);
      });
    }
    return getParameters().myGeneralSettingsCache;
  }

  public MavenImportingSettings getImportingSettings() {
    if (getParameters().myImportingSettingsCache == null) {
      ApplicationManager.getApplication().runReadAction(() -> {
        getParameters().myImportingSettingsCache = getDirectProjectsSettings().getImportingSettings().clone();
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
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public @Nullable VirtualFile getRootDirectory() {
    Path rootPath = getRootPath();
    return rootPath == null ? null : VfsUtil.findFile(rootPath, false);
  }

  public @Nullable Path getRootPath() {
    if (getParameters().myImportRootDirectory == null && isUpdate()) {
      Project project = getProjectToUpdate();
      getParameters().myImportRootDirectory = project == null ? null : Paths.get(Objects.requireNonNull(project.getBasePath()));
    }
    return getParameters().myImportRootDirectory;
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
    getParameters().myImportRootDirectory = Files.isDirectory(path) ? path : path.getParent();
  }

  public void setFileToImport(@NotNull VirtualFile file) {
    if (!file.isDirectory()) getParameters().myImportProjectFile = file;
    getParameters().myImportRootDirectory = file.isDirectory() ? file.toNioPath() : file.getParent().toNioPath();
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

  private List<VirtualFile> getProjectFiles(@NotNull MavenProgressIndicator indicator) throws MavenProcessCanceledException {
    if (getParameters().myImportProjectFile != null) {
      return Collections.singletonList(getParameters().myImportProjectFile);
    }
    Path file = getRootPath();
    VirtualFile virtualFile = file == null ? null : LocalFileSystem.getInstance()
      .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()));
    if (virtualFile == null) {
      throw new MavenProcessCanceledException();
    }
    return FileFinder.findPomFiles(virtualFile.getChildren(), LookForNestedToggleAction.isSelected(), indicator);
  }
}
