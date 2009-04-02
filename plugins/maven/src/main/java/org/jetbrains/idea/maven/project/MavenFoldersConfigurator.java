package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectRootManager;
import org.jetbrains.idea.maven.facets.FacetImporter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenFoldersConfigurator {
  private MavenProject myMavenProject;
  private MavenImportingSettings myImportingSettings;
  private MavenRootModelAdapter myModel;

  public static void updateProjectFolders(final Project project, final boolean updateTargetFoldersOnly) {
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    final MavenImportingSettings settings = manager.getImportingSettings();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        List<ModifiableRootModel> rootModels = new ArrayList<ModifiableRootModel>();
        for (Module each : ModuleManager.getInstance(project).getModules()) {
          MavenProject project = manager.findProject(each);
          if (project == null) continue;

          MavenRootModelAdapter a = new MavenRootModelAdapter(each, null);
          new MavenFoldersConfigurator(project, settings, a).config(updateTargetFoldersOnly);

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

  public MavenFoldersConfigurator(MavenProject mavenProject, MavenImportingSettings settings, MavenRootModelAdapter model) {
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

    for (FacetImporter each : FacetImporter.getSuitableFacetImporters(myMavenProject)) {
      sourceFolders.addAll(each.getSourceFolders(myMavenProject));
      testFolders.addAll(each.getTestFolders(myMavenProject));
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
    else {
      myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
      myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
    }
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

    for (FacetImporter<?, ?, ?> each : FacetImporter.getSuitableFacetImporters(myMavenProject)) {
      for (String eachFolder : each.getExcludedFolders(myMavenProject)) {
        myModel.unregisterAll(eachFolder, true, true);
        myModel.addExcludedFolder(eachFolder);
      }
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
