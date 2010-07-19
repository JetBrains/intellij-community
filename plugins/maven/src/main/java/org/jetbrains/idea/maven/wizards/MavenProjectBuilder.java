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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenUIModifiableModelsProvider;
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
    private List<String> myProfiles = new ArrayList<String>();
    private List<String> mySelectedProfiles = new ArrayList<String>();

    private MavenProjectsTree myMavenProjectTree;

    private boolean myOpenModulesConfigurator;
  }

  private Parameters myParamaters;

  public String getName() {
    return ProjectBundle.message("maven.name");
  }

  public Icon getIcon() {
    return MavenIcons.MAVEN_ICON;
  }

  public void cleanup() {
    myParamaters = null;
    super.cleanup();
  }

  @Override
  public boolean isSuitableSdk(Sdk sdk) {
    return sdk.getSdkType() == JavaSdk.getInstance();
  }

  private Parameters getParameters() {
    if (myParamaters == null) {
      myParamaters = new Parameters();
    }
    return myParamaters;
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
    MavenWorkspaceSettings settings = project.getComponent(MavenWorkspaceSettingsComponent.class).getState();

    settings.generalSettings = getGeneralSettings();
    settings.importingSettings = getImportingSettings();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    List<VirtualFile> files = getParameters().myMavenProjectTree.getRootProjectsFiles();
    manager.addManagedFilesWithProfiles(files, getSelectedProfiles());
    manager.waitForReadingCompletion();

    boolean isFromUI = model != null;
    return manager.importProjects(isFromUI
                                  ? new MavenUIModifiableModelsProvider(project, model, (ModulesConfigurator)modulesProvider, artifactModel)
                                  : new MavenDefaultModifiableModelsProvider(project));
  }

  public VirtualFile getRootDirectory() {
    return getImportRoot();
  }

  public boolean setRootDirectory(final String root) throws ConfigurationException {
    getParameters().myFiles = null;
    getParameters().myProfiles.clear();
    getParameters().myMavenProjectTree = null;

    getParameters().myImportRoot = FileFinder.refreshRecursively(root);
    if (getParameters().myImportRoot == null) return false;

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenTask() {
      public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
        indicator.setText(ProjectBundle.message("maven.locating.files"));
        getParameters().myFiles = FileFinder.findPomFiles(getImportRoot().getChildren(),
                                                          getImportingSettings().isLookForNested(),
                                                          indicator.getIndicator(),
                                                          new ArrayList<VirtualFile>());

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

    Set<String> uniqueProfiles = new LinkedHashSet<String>();
    MavenProjectReader reader = new MavenProjectReader();
    MavenGeneralSettings generalSettings = getGeneralSettings();
    MavenProjectReaderProjectLocator locator = new MavenProjectReaderProjectLocator() {
      public VirtualFile findProjectFile(MavenId coordinates) {
        return null;
      }
    };
    for (VirtualFile f : getParameters().myFiles) {
      MavenProject project = new MavenProject(f);
      process.setText2(ProjectBundle.message("maven.reading.pom", f.getPath()));
      project.read(generalSettings, Collections.<String>emptyList(), reader, locator);
      uniqueProfiles.addAll(project.getProfilesIds());
    }
    getParameters().myProfiles = new ArrayList<String>(uniqueProfiles);
  }

  public List<String> getProfiles() {
    return getParameters().myProfiles;
  }

  public List<String> getSelectedProfiles() {
    return getParameters().mySelectedProfiles;
  }

  public boolean setSelectedProfiles(List<String> profiles) {
    getParameters().myMavenProjectTree = null;
    getParameters().mySelectedProfiles = profiles;

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenTask() {
      public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
        readMavenProjectTree(indicator);
        indicator.setText2("");
      }
    });
  }

  private boolean runConfigurationProcess(String message, MavenTask p) {
    try {
      MavenUtil.run(null, message, p);
    }
    catch (MavenProcessCanceledException e) {
      return false;
    }
    return true;
  }

  private void readMavenProjectTree(MavenProgressIndicator process) throws MavenProcessCanceledException {
    MavenProjectsTree tree = new MavenProjectsTree();
    tree.addManagedFilesWithProfiles(getParameters().myFiles, getParameters().mySelectedProfiles);
    tree.updateAll(false, getGeneralSettings(), process, null);
    getParameters().myMavenProjectTree = tree;
  }

  public List<MavenProject> getList() {
    return getParameters().myMavenProjectTree.getRootProjects();
  }

  public void setList(List<MavenProject> projects) {
  }

  public boolean isMarked(final MavenProject element) {
    return true;
  }

  public boolean isOpenProjectSettingsAfter() {
    return getParameters().myOpenModulesConfigurator;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    getParameters().myOpenModulesConfigurator = on;
  }

  public MavenGeneralSettings getGeneralSettings() {
    if (getParameters().myGeneralSettingsCache == null) {
      getParameters().myGeneralSettingsCache = getDirectProjectsSettings().generalSettings.clone();
    }
    return getParameters().myGeneralSettingsCache;
  }

  public MavenImportingSettings getImportingSettings() {
    if (getParameters().myImportingSettingsCache == null) {
      getParameters().myImportingSettingsCache = getDirectProjectsSettings().importingSettings.clone();
    }
    return getParameters().myImportingSettingsCache;
  }

  private MavenWorkspaceSettings getDirectProjectsSettings() {
    return ApplicationManager.getApplication().runReadAction(new Computable<MavenWorkspaceSettings>() {
      @Override
      public MavenWorkspaceSettings compute() {
        return getProject().getComponent(MavenWorkspaceSettingsComponent.class).getState();
      }
    });
  }

  @NotNull
  private Project getProject() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Project>() {
      @Override
      public Project compute() {
        Project result = isUpdate() ? getProjectToUpdate() : null;
        if (result == null || result.isDisposed()) result = ProjectManager.getInstance().getDefaultProject();
        return result;
      }
    });
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

  @Nullable
  public VirtualFile getImportRoot() {
    if (getParameters().myImportRoot == null && isUpdate()) {
      final Project project = getProjectToUpdate();
      assert project != null;
      getParameters().myImportRoot = project.getBaseDir();
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
}
