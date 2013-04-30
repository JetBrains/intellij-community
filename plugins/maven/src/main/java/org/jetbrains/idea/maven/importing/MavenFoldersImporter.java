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
package org.jetbrains.idea.maven.importing;

import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MavenFoldersImporter {
  private final MavenProject myMavenProject;
  private final MavenImportingSettings myImportingSettings;
  private final MavenRootModelAdapter myModel;

  public static void updateProjectFolders(final Project project, final boolean updateTargetFoldersOnly) {
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    final MavenImportingSettings settings = manager.getImportingSettings();

    AccessToken accessToken = WriteAction.start();
    try {
      List<ModifiableRootModel> rootModels = new ArrayList<ModifiableRootModel>();
      for (Module each : ModuleManager.getInstance(project).getModules()) {
        MavenProject mavenProject = manager.findProject(each);
        if (mavenProject == null) continue;

        MavenRootModelAdapter a = new MavenRootModelAdapter(mavenProject, each, new MavenDefaultModifiableModelsProvider(project));
        new MavenFoldersImporter(mavenProject, settings, a).config(updateTargetFoldersOnly);

        ModifiableRootModel model = a.getRootModel();
        if (model.isChanged()) {
          rootModels.add(model);
        }
        else {
          model.dispose();
        }
      }

      if (!rootModels.isEmpty()) {
        ModifiableRootModel[] modelsArray = rootModels.toArray(new ModifiableRootModel[rootModels.size()]);
        if (modelsArray.length > 0) {
          ModifiableModelCommitter.multiCommit(modelsArray, ModuleManager.getInstance(modelsArray[0].getProject()).getModifiableModel());
        }
      }
    }
    finally {
      accessToken.finish();
    }
  }

  public MavenFoldersImporter(MavenProject mavenProject, MavenImportingSettings settings, MavenRootModelAdapter model) {
    myMavenProject = mavenProject;
    myImportingSettings = settings;
    myModel = model;
  }

  public void config() {
    config(false);
  }

  private void config(boolean updateTargetFoldersOnly) {
    if (!updateTargetFoldersOnly) {
      if (!myImportingSettings.isKeepSourceFolders()) {
        myModel.clearSourceFolders();
      }
      configSourceFolders();
      configOutputFolders();
    }
    configGeneratedAndExcludedFolders();
  }

  private void configSourceFolders() {
    List<String> sourceFolders = new ArrayList<String>();
    List<String> testFolders = new ArrayList<String>();

    sourceFolders.addAll(myMavenProject.getSources());
    testFolders.addAll(myMavenProject.getTestSources());

    for (MavenImporter each : MavenImporter.getSuitableImporters(myMavenProject)) {
      each.collectSourceFolders(myMavenProject, sourceFolders);
      each.collectTestFolders(myMavenProject, testFolders);
    }

    for (MavenResource each : myMavenProject.getResources()) {
      sourceFolders.add(each.getDirectory());
    }
    for (MavenResource each : myMavenProject.getTestResources()) {
      testFolders.add(each.getDirectory());
    }

    addBuilderHelperPaths("add-source", sourceFolders);
    addBuilderHelperPaths("add-test-source", testFolders);

    List<Pair<Path, Boolean>> allFolders = new ArrayList<Pair<Path, Boolean>>(sourceFolders.size() + testFolders.size());
    for (String each : sourceFolders) {
      allFolders.add(Pair.create(myModel.toPath(each), false));
    }
    for (String each : testFolders) {
      allFolders.add(Pair.create(myModel.toPath(each), true));
    }

    for (Pair<Path, Boolean> each : normalize(allFolders)) {
      myModel.addSourceFolder(each.first.getPath(), each.second);
    }
  }

  private void addBuilderHelperPaths(String goal, List<String> folders) {
    final Element configurationElement = myMavenProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", goal);
    if (configurationElement != null) {
      final Element sourcesElement = configurationElement.getChild("sources");
      if (sourcesElement != null) {
        //noinspection unchecked
        for (Element element : (List<Element>)sourcesElement.getChildren()) {
          folders.add(element.getTextTrim());
        }
      }
    }
  }

  @NotNull
  private static List<Pair<Path, Boolean>> normalize(@NotNull List<Pair<Path, Boolean>> folders) {
    List<Pair<Path, Boolean>> result = new ArrayList<Pair<Path, Boolean>>(folders.size());
    for (Pair<Path, Boolean> eachToAdd : folders) {
      addSourceFolder(eachToAdd, result);
    }
    return result;
  }

  private static void addSourceFolder(Pair<Path, Boolean> folder, List<Pair<Path, Boolean>> result) {
    for (Pair<Path, Boolean> eachExisting : result) {
      if (MavenRootModelAdapter.isEqualOrAncestor(eachExisting.first.getPath(), folder.first.getPath())
          || MavenRootModelAdapter.isEqualOrAncestor(folder.first.getPath(), eachExisting.first.getPath())) {
        return;
      }
    }
    result.add(folder);
  }

  private void configOutputFolders() {
    if (myImportingSettings.isUseMavenOutput()) {
      myModel.useModuleOutput(myMavenProject.getOutputDirectory(),
                              myMavenProject.getTestOutputDirectory());
    }
    myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
    myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
  }

  private void configGeneratedAndExcludedFolders() {
    File targetDir = new File(myMavenProject.getBuildDirectory());

    String generatedDir = myMavenProject.getGeneratedSourcesDirectory(false);
    String generatedDirTest = myMavenProject.getGeneratedSourcesDirectory(true);

    myModel.unregisterAll(targetDir.getPath(), true, false);

    if (myImportingSettings.getGeneratedSourcesFolder() != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
      myModel.addSourceFolder(myMavenProject.getAnnotationProcessorDirectory(true), true, true);
      myModel.addSourceFolder(myMavenProject.getAnnotationProcessorDirectory(false), false, true);
    }

    File[] targetChildren = targetDir.listFiles();

    if (targetChildren != null) {
      for (File f : targetChildren) {
        if (!f.isDirectory()) continue;

        if (FileUtil.pathsEqual(generatedDir, f.getPath())) {
          configGeneratedSourceFolder(f, false);
        }
        else if (FileUtil.pathsEqual(generatedDirTest, f.getPath())) {
          configGeneratedSourceFolder(f, true);
        }
        else {
          if (myImportingSettings.isExcludeTargetFolder()) {
            if (myModel.hasRegisteredSourceSubfolder(f)) continue;
            if (myModel.isAlreadyExcluded(f)) continue;
            myModel.addExcludedFolder(f.getPath());
          }
        }
      }
    }

    List<String> facetExcludes = new ArrayList<String>();
    for (MavenImporter each : MavenImporter.getSuitableImporters(myMavenProject)) {
      each.collectExcludedFolders(myMavenProject, facetExcludes);
    }
    for (String eachFolder : facetExcludes) {
      myModel.unregisterAll(eachFolder, true, true);
      myModel.addExcludedFolder(eachFolder);
    }

    if (myImportingSettings.isExcludeTargetFolder()) {
      if (targetChildren == null || !myModel.hasRegisteredSourceSubfolder(targetDir)) {
        myModel.addExcludedFolder(targetDir.getPath());
      }
    }
    else {
      myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
      myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
    }
  }

  private void configGeneratedSourceFolder(@NotNull File targetDir, boolean isTestSources) {
    switch (myImportingSettings.getGeneratedSourcesFolder()) {
      case GENERATED_SOURCE_FOLDER:
        myModel.addSourceFolder(targetDir.getPath(), isTestSources, true);
        break;

      case SUBFOLDER:
        addAllSubDirsAsSources(targetDir, isTestSources);
        break;

      case AUTODETECT:
        Collection<JavaModuleSourceRoot> sourceRoots = JavaSourceRootDetectionUtil.suggestRoots(targetDir);

        for (JavaModuleSourceRoot root : sourceRoots) {
          if (targetDir.equals(root.getDirectory())) {
            myModel.addSourceFolder(targetDir.getPath(), isTestSources);
            return;
          }

          addAsSourceFolder(root.getDirectory(), isTestSources);
        }

        addAllSubDirsAsSources(targetDir, isTestSources);
        break;

      case IGNORE:
        break; // Ignore.
    }
  }

  private void addAsSourceFolder(@NotNull File dir, boolean isTestSources) {
    if (!myModel.hasRegisteredSourceSubfolder(dir)) {
      myModel.addSourceFolder(dir.getPath(), isTestSources, true);
    }
  }

  private void addAllSubDirsAsSources(@NotNull File dir, boolean isTestSources) {
    for (File f : getChildren(dir)) {
      if (f.isDirectory()) {
        addAsSourceFolder(f, isTestSources);
      }
    }
  }

  private static File[] getChildren(File dir) {
    File[] result = dir.listFiles();
    return result == null ? ArrayUtil.EMPTY_FILE_ARRAY : result;
  }
}
