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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectRootManager;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenFoldersImporter {
  private final MavenProject myMavenProject;
  private final MavenImportingSettings myImportingSettings;
  private final MavenRootModelAdapter myModel;

  public static void updateProjectFolders(final Project project, final boolean updateTargetFoldersOnly) {
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    final MavenImportingSettings settings = manager.getImportingSettings();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
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
          ProjectRootManager.getInstance(project).multiCommit(modelsArray);
        }
      }
    });
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
      configSourceFolders();
      configOutputFolders();
    }
    configExcludedFolders();
  }

  private void configSourceFolders() {
    List<String> sourceFolders = new ArrayList<String>();
    List<String> testFolders = new ArrayList<String>();

    sourceFolders.addAll(myMavenProject.getSources());
    testFolders.addAll(myMavenProject.getTestSources());

    for (MavenResource each : myMavenProject.getResources()) {
      sourceFolders.add(each.getDirectory());
    }
    for (MavenResource each : myMavenProject.getTestResources()) {
      testFolders.add(each.getDirectory());
    }

    for (MavenImporter each : MavenImporter.getSuitableImporters(myMavenProject)) {
      each.collectSourceFolders(myMavenProject, sourceFolders);
      each.collectTestFolders(myMavenProject, testFolders);
    }

    for (String each : sourceFolders) {
      myModel.addSourceFolder(each, false);
    }
    for (String each : testFolders) {
      myModel.addSourceFolder(each, true);
    }
  }

  private void configOutputFolders() {
    if (myImportingSettings.isUseMavenOutput()) {
      myModel.useModuleOutput(myMavenProject.getOutputDirectory(),
                              myMavenProject.getTestOutputDirectory());
    }
    myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
    myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
  }

  private void configExcludedFolders() {
    File targetDir = new File(myMavenProject.getBuildDirectory());
    File generatedDir = new File(myMavenProject.getGeneratedSourcesDirectory());

    myModel.unregisterAll(targetDir.getPath(), true, false);

    for (File f : getChildren(targetDir)) {
      if (!f.isDirectory()) continue;

      if (f.equals(generatedDir)) {
        addAllSubDirsAsSources(f);
      }
      else {
        if (myModel.hasRegisteredSourceSubfolder(f)) continue;
        if (myModel.isAlreadyExcluded(f)) continue;
        myModel.addExcludedFolder(f.getPath());
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

    if (!myModel.hasRegisteredSourceSubfolder(targetDir)) {
      myModel.addExcludedFolder(targetDir.getPath());
    }
  }

  private void addAllSubDirsAsSources(File dir) {
    for (File f : getChildren(dir)) {
      if (!f.isDirectory()) continue;
      if (myModel.hasRegisteredSourceSubfolder(f)) continue;
      myModel.addSourceFolder(f.getPath(), false);
    }
  }

  private File[] getChildren(File dir) {
    File[] result = dir.listFiles();
    return result == null ? new File[0] : result;
  }
}
