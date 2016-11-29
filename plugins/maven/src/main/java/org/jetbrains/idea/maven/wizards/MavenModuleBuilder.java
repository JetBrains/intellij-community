/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.wizards;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MavenModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {

  private MavenProject myAggregatorProject;
  private MavenProject myParentProject;

  private boolean myInheritGroupId;
  private boolean myInheritVersion;

  private MavenId myProjectId;
  private MavenArchetype myArchetype;

  private MavenEnvironmentForm myEnvironmentForm;

  private Map<String, String> myPropertiesToCreateByArtifact;

  public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
    final Project project = rootModel.getProject();

    final VirtualFile root = createAndGetContentEntry();
    rootModel.addContentEntry(root);

    // todo this should be moved to generic ModuleBuilder
    if (myJdk != null){
      rootModel.setSdk(myJdk);
    } else {
      rootModel.inheritSdk();
    }

    MavenUtil.runWhenInitialized(project, new DumbAwareRunnable() {
      public void run() {
        if (myEnvironmentForm != null) {
          myEnvironmentForm.setData(MavenProjectsManager.getInstance(project).getGeneralSettings());
        }

        new MavenModuleBuilderHelper(myProjectId, myAggregatorProject, myParentProject, myInheritGroupId,
                                     myInheritVersion, myArchetype, myPropertiesToCreateByArtifact, "Create new Maven module").configure(project, root, false);
      }
    });
  }

  @Override
  public String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public String getPresentableName() {
    return "Maven";
  }

  @Override
  public String getParentGroup() {
    return JavaModuleType.BUILD_TOOLS_GROUP;
  }

  @Override
  public int getWeight() {
    return JavaModuleBuilder.BUILD_SYSTEM_WEIGHT;
  }

  @Override
  public String getDescription() {
    return "Maven modules are used for developing <b>JVM-based</b> applications with dependencies managed by <b>Maven</b>. " +
           "You can create either a blank Maven module or a module based on a <b>Maven archetype</b>.";
  }

  @Override
  public Icon getBigIcon() {
    return AllIcons.Modules.Types.JavaModule;
  }

  @Override
  public Icon getNodeIcon() {
    return MavenIcons.MavenLogo;
  }

  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == JavaSdk.getInstance();
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return new ModuleWizardStep[]{
      new MavenModuleWizardStep(this, wizardContext, !wizardContext.isNewWizard()),
      new SelectPropertiesStep(wizardContext.getProject(), this)
    };
  }

  private VirtualFile createAndGetContentEntry() {
    String path = FileUtil.toSystemIndependentName(getContentEntryPath());
    new File(path).mkdirs();
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
  }

  public List<Pair<String, String>> getSourcePaths() {
    return Collections.emptyList();
  }

  public void setSourcePaths(List<Pair<String, String>> sourcePaths) {
  }

  public void addSourcePath(Pair<String, String> sourcePathInfo) {
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

  public boolean isInheritVersion() {
    return myInheritVersion;
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
  public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
    MavenArchetypesStep step = new MavenArchetypesStep(this, null);
    Disposer.register(parentDisposable, step);
    return step;
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    final JTextField moduleNameField = settingsStep.getModuleNameField();
    if (moduleNameField != null && myProjectId != null && myProjectId.getArtifactId() != null) {
      moduleNameField.setText(StringUtil.sanitizeJavaIdentifier(myProjectId.getArtifactId()));
    }
    return super.modifySettingsStep(settingsStep);
  }
}
