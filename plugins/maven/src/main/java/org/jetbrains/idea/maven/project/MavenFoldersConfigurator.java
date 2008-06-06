package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenFoldersConfigurator {
  private MavenProjectModel myMavenProject;
  private MavenImporterSettings myPrefs;
  private RootModelAdapter myModel;

  public static void updateProjectFolders(final Project project, final List<MavenProject> updatedProjects) {
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    final MavenImporterSettings settings = manager.getImporterSettings();

    final Map<VirtualFile, MavenProject> fileToProjectMapping = new HashMap<VirtualFile, MavenProject>();

    for (MavenProject each : updatedProjects) {
      VirtualFile f = LocalFileSystem.getInstance().findFileByIoFile(each.getFile());
      if (f != null) fileToProjectMapping.put(f, each);
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        List<ModifiableRootModel> rootModels = new ArrayList<ModifiableRootModel>();
        for (Module each : ModuleManager.getInstance(project).getModules()) {
          MavenProjectModel project = manager.findProject(each);
          if (project == null) continue;

          MavenProject mavenProject = fileToProjectMapping.get(project.getFile());
          if (mavenProject != null)  {
            project.updateFolders(mavenProject);
          }

          RootModelAdapter a = new RootModelAdapter(each);
          new MavenFoldersConfigurator(project, settings, a).config();

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

  public MavenFoldersConfigurator(MavenProjectModel mavenProject, MavenImporterSettings settings, RootModelAdapter model) {
    myMavenProject = mavenProject;
    myPrefs = settings;
    myModel = model;
  }

  public void config() {
    configSourceFolders();
    configOutputFolders();
    configFoldersUnderTargetDir();
  }

  private void configSourceFolders() {
    for (String o : myMavenProject.getCompileSourceRoots()) {
      myModel.addSourceFolder(o, false);
    }
    for (String o : myMavenProject.getTestCompileSourceRoots()) {
      myModel.addSourceFolder(o, true);
    }

    for (Resource o : myMavenProject.getResources()) {
      myModel.addSourceFolder(o.getDirectory(), false);
    }
    for (Resource each : myMavenProject.getTestResources()) {
      myModel.addSourceFolder(each.getDirectory(), true);
    }
  }

  private void configOutputFolders() {
    if (myPrefs.isUseMavenOutput()) {
      myModel.useModuleOutput(myMavenProject.getOutputDirectory(),
                              myMavenProject.getTestOutputDirectory());
    }
    else {
      myModel.useProjectOutput();
      myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
      myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
    }
  }

  private void configFoldersUnderTargetDir() {
    File targetDir = new File(myMavenProject.getBuildDirectory());

    for (File f : getChildren(targetDir)) {
      if (!f.isDirectory()) continue;

      if (FileUtil.pathsEqual(f.getName(), "generated-sources")) {
        addAllSubDirsAsSources(f);
      }
      else {
        if (myModel.hasRegisteredSourceSubfolder(f)) continue;
        if (myModel.isAlreadyExcluded(f)) continue;
        myModel.addExcludedFolder(f.getPath());
      }
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
