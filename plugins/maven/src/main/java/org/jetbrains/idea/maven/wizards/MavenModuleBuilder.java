/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.io.File;
import java.util.Collections;
import java.util.List;

public class MavenModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {
  private static final Icon BIG_ICON = IconLoader.getIcon("/modules/javaModule.png");

  private String myContentRootPath;

  private MavenProject myAggregatorProject;
  private MavenProject myParentProject;

  private boolean myInheritGroupId;
  private boolean myInheritVersion;

  private MavenId myProjectId;
  private MavenArchetype myArchetype;

  public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
    final Project project = rootModel.getProject();

    final VirtualFile root = createAndGetContentEntry();
    rootModel.addContentEntry(root);

    rootModel.inheritSdk();

    MavenUtil.runWhenInitialized(project, new DumbAwareRunnable() {
      public void run() {
        new MavenModuleBuilderHelper(myProjectId, myAggregatorProject, myParentProject, myInheritGroupId,
                                     myInheritVersion, myArchetype,"Create new Maven module").configure(project, root, false);
      }
    });
  }

  @Override
  public String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public String getPresentableName() {
    return "Maven Module";
  }

  @Override
  public String getDescription() {
    return "Creates a blank Maven module or from Maven Archetype";
  }

  @Override
  public Icon getBigIcon() {
    return BIG_ICON;
  }

  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, ModulesProvider modulesProvider) {
    return new ModuleWizardStep[]{new MavenModuleWizardStep(wizardContext.getProject(), this)};
  }

  public MavenProject findPotentialParentProject(Project project) {
    if (!MavenProjectsManager.getInstance(project).isMavenizedProject()) return null;

    File parentDir = new File(myContentRootPath).getParentFile();
    if (parentDir == null) return null;
    VirtualFile parentPom = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(parentDir, "pom.xml"));
    if (parentPom == null) return null;

    return MavenProjectsManager.getInstance(project).findProject(parentPom);
  }

  private VirtualFile createAndGetContentEntry() {
    String path = FileUtil.toSystemIndependentName(myContentRootPath);
    new File(path).mkdirs();
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
  }

  public String getContentEntryPath() {
    return myContentRootPath;
  }

  public void setContentEntryPath(String path) {
    myContentRootPath = path;
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
}
