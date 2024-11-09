// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporter;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static icons.OpenapiIcons.RepositoryLibraryLogo;

public abstract class AbstractMavenModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {

  private boolean isCreatingNewProject;

  protected MavenProject myAggregatorProject;
  protected MavenProject myParentProject;

  protected boolean myInheritGroupId;
  protected boolean myInheritVersion;

  protected MavenId myProjectId;
  protected MavenArchetype myArchetype;

  protected MavenEnvironmentForm myEnvironmentForm;

  protected Map<String, String> myPropertiesToCreateByArtifact;

  @ApiStatus.Internal
  public CompletableFuture<Boolean> sdkDownloadedFuture;

  @Override
  public @NotNull Module createModule(@NotNull ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, ConfigurationException, JDOMException {
    var module = super.createModule(moduleModel);

    // handle the case when a maven module was deleted / ignored, and then the same module is created again
    // we need to remove the module from the ignored list, otherwise it will disappear during the subsequent maven import
    unignorePom(moduleModel);

    return module;
  }

  private void unignorePom(@NotNull ModifiableModuleModel moduleModel) {
    var project = moduleModel.getProject();
    var contentEntryPath = getContentEntryPath();
    if (null == contentEntryPath) return;
    var mavenProjectsTree = MavenProjectsManager.getInstance(project).getProjectsTree();
    mavenProjectsTree.removeIgnoredFilesPaths(List.of(contentEntryPath + "/pom.xml"));
  }

  @Override
  protected void setupModule(Module module) throws ConfigurationException {
    super.setupModule(module);
    var moduleVersion = WorkspaceModuleImporter.ExternalSystemData.VERSION;
    ExternalSystemUtil.markModuleAsMaven(module, moduleVersion, true);
  }

  @Override
  public void setupRootModel(@NotNull ModifiableRootModel rootModel) {
    final Project project = rootModel.getProject();

    final VirtualFile root = createAndGetContentEntry();
    rootModel.addContentEntry(root);

    inheritOrSetSDK(rootModel);

    if (isCreatingNewProject) {
      setupNewProject(project);
    }

    MavenUtil.runWhenInitialized(project, (DumbAwareRunnable)() -> {
      configure(project, root);
    });
  }

  private void configure(Project project, VirtualFile root) {
    if (myEnvironmentForm != null) {
      myEnvironmentForm.setData(MavenProjectsManager.getInstance(project).getGeneralSettings());
    }

    var future = sdkDownloadedFuture;
    if (null != future) {
      try {
        future.get(); // maven sync uses project JDK
      }
      catch (Exception e) {
        MavenLog.LOG.error(e);
      }
    }

    new MavenModuleBuilderHelper(myProjectId, myAggregatorProject, myParentProject, myInheritGroupId,
                                 myInheritVersion, myArchetype, myPropertiesToCreateByArtifact,
                                 MavenProjectBundle.message("command.name.create.new.maven.module")).configure(project, root, false);
  }

  protected static void setupNewProject(Project project) {
    ExternalProjectsManagerImpl.setupCreatedProject(project);
    project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, true);
  }

  protected void inheritOrSetSDK(@NotNull ModifiableRootModel rootModel) {
    // todo this should be moved to generic ModuleBuilder
    var projectSdk = ProjectRootManager.getInstance(rootModel.getProject()).getProjectSdk();
    if (myJdk == null || equalSdks(myJdk, projectSdk)) {
      rootModel.inheritSdk();
    }
    else {
      rootModel.setSdk(myJdk);
    }
  }

  protected static boolean equalSdks(Sdk sdk1, Sdk sdk2) {
    if (sdk1 == null && sdk2 == null) return true;
    if (sdk1 == null || sdk2 == null) return false;
    return sdk1.getSdkType() == sdk2.getSdkType()
           && StringUtil.equals(sdk1.getName(), sdk2.getName())
           && StringUtil.equals(sdk1.getVersionString(), sdk2.getVersionString())
           && StringUtil.equals(sdk1.getHomePath(), sdk2.getHomePath())
      ;
  }

  @Override
  public @NonNls String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public String getPresentableName() {
    return MavenProjectBundle.message("configurable.MavenSettings.display.name");
  }

  @Override
  public String getParentGroup() {
    return JavaModuleType.JAVA_GROUP;
  }

  @Override
  public int getWeight() {
    return JavaModuleBuilder.BUILD_SYSTEM_WEIGHT;
  }

  @Override
  public String getDescription() {
    return MavenWizardBundle.message("maven.builder.module.builder.description");
  }

  @Override
  public Icon getNodeIcon() {
    return RepositoryLibraryLogo;
  }

  @Override
  public ModuleType<?> getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == JavaSdk.getInstance();
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  protected VirtualFile createAndGetContentEntry() {
    String path = FileUtil.toSystemIndependentName(getContentEntryPath());
    try {
      Files.createDirectory(Path.of(path));
    }
    catch (IOException e) {
      // ignore
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
  }

  @Override
  public List<Pair<String, String>> getSourcePaths() {
    return Collections.emptyList();
  }

  @Override
  public void setSourcePaths(List<Pair<String, String>> sourcePaths) {
  }

  @Override
  public void addSourcePath(Pair<String, String> sourcePathInfo) {
  }

  public boolean isCreatingNewProject() {
    return isCreatingNewProject;
  }

  public void setCreatingNewProject(boolean creatingNewProject) {
    isCreatingNewProject = creatingNewProject;
  }

  public void setAggregatorProject(MavenProject project) {
    myAggregatorProject = project;
  }

  public MavenProject getAggregatorProject() {
    return myAggregatorProject;
  }

  public void setParentProject(MavenProject project) {
    myParentProject = project;
  }

  public MavenProject getParentProject() {
    return myParentProject;
  }

  public void setInheritedOptions(boolean groupId, boolean version) {
    myInheritGroupId = groupId;
    myInheritVersion = version;
  }

  public boolean isInheritGroupId() {
    return myInheritGroupId;
  }

  public void setInheritGroupId(boolean inheritGroupId) {
    myInheritGroupId = inheritGroupId;
  }

  public boolean isInheritVersion() {
    return myInheritVersion;
  }

  public void setInheritVersion(boolean inheritVersion) {
    myInheritVersion = inheritVersion;
  }

  public void setProjectId(MavenId id) {
    myProjectId = id;
  }

  public MavenId getProjectId() {
    return myProjectId;
  }

  public void setArchetype(MavenArchetype archetype) {
    myArchetype = archetype;
  }

  public MavenArchetype getArchetype() {
    return myArchetype;
  }

  public MavenEnvironmentForm getEnvironmentForm() {
    return myEnvironmentForm;
  }

  public void setEnvironmentForm(MavenEnvironmentForm environmentForm) {
    myEnvironmentForm = environmentForm;
  }

  public Map<String, String> getPropertiesToCreateByArtifact() {
    return myPropertiesToCreateByArtifact;
  }

  public void setPropertiesToCreateByArtifact(Map<String, String> propertiesToCreateByArtifact) {
    myPropertiesToCreateByArtifact = propertiesToCreateByArtifact;
  }

  @Override
  public String getGroupName() {
    return "Maven";
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    final ModuleNameLocationSettings nameLocationSettings = settingsStep.getModuleNameLocationSettings();
    if (nameLocationSettings != null && myProjectId != null && myProjectId.getArtifactId() != null) {
      nameLocationSettings.setModuleName(StringUtil.sanitizeJavaIdentifier(myProjectId.getArtifactId()));
      if (myAggregatorProject != null) {
        nameLocationSettings.setModuleContentRoot(myAggregatorProject.getDirectory() + "/" + myProjectId.getArtifactId());
      }
    }
    return super.modifySettingsStep(settingsStep);
  }

  @Nullable
  @Override
  public Project createProject(String name, String path) {
    setCreatingNewProject(true);
    return super.createProject(name, path);
  }

  @Override
  public @Nullable Consumer<Module> createModuleConfigurator() {
    return module -> {
      VirtualFile root = ModuleRootManager.getInstance(module).getContentEntries()[0].getFile();
      configure(module.getProject(), root);
    };
  }
}
