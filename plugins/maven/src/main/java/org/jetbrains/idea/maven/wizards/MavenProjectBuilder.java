// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.DeprecatedProjectBuilderForImport;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectOpenProcessor;
import icons.OpenapiIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction;
import org.jetbrains.idea.maven.utils.*;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Do not use this project import builder directly.
 * <p>
 * Internal stable Api
 * Use {@link com.intellij.ide.actions.ImportModuleAction#createFromWizard} to import (attach) a new project.
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
    return OpenapiIcons.RepositoryLibraryLogo;
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

  public VirtualFile getProjectFileToImport() {
    var projectFile = getParameters().myImportProjectFile;
    if (null != projectFile) return projectFile;

    var importRootDirectory = getParameters().myImportRootDirectory;
    if (null != importRootDirectory) {
      return VirtualFileManager.getInstance().findFileByNioPath(importRootDirectory);
    }

    return null;
  }

  private List<Module> commitWithAsyncBuilder(Project project,
                                              ModifiableModuleModel model,
                                              ModulesProvider modulesProvider,
                                              ModifiableArtifactModel artifactModel) {
    var projectFile = getProjectFileToImport();
    if (null == projectFile) {
      LOG.warn("Project file missing");
      return List.of();
    }

    IdeUIModifiableModelsProvider modelsProvider = null;
    if (model != null) {
      modelsProvider = new IdeUIModifiableModelsProvider(project, model, (ModulesConfigurator)modulesProvider, artifactModel);
    }

    return new MavenProjectAsyncBuilder().commitSync(project, projectFile, modelsProvider);
  }


  @Override
  public List<Module> commit(Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {
    return commitWithAsyncBuilder(project, model, modulesProvider, artifactModel);
  }

  /**
   * @deprecated Use {@link #setRootDirectory(Project, Path)}
   */
  @Deprecated(forRemoval = true)
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

  private static boolean runConfigurationProcess(@NlsContexts.DialogTitle String message, MavenTask p) {
    try {
      MavenUtil.run(message, p);
    }
    catch (MavenProcessCanceledException e) {
      return false;
    }
    return true;
  }

  private void readMavenProjectTree(MavenProgressIndicator process) {
    MavenProjectsTree tree = new MavenProjectsTree(getProjectOrDefault());
    tree.addManagedFilesWithProfiles(getParameters().myFiles, MavenExplicitProfiles.NONE);
    tree.updateAll(false, getGeneralSettings(), process.getIndicator());

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
        List<VirtualFile> rootFiles = getParameters().myFiles;
        if (rootFiles == null) {
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

  public @Nullable Path getRootPath() {
    if (getParameters().myImportRootDirectory == null && isUpdate()) {
      Project project = getProjectToUpdate();
      getParameters().myImportRootDirectory = project == null ? null : Paths.get(Objects.requireNonNull(project.getBasePath()));
    }
    return getParameters().myImportRootDirectory;
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
    Project project = super.createProject(name, path);
    if (project != null) {
      ExternalProjectsManagerImpl.setupCreatedProject(project);
      project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, true);
    }
    return project;
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
