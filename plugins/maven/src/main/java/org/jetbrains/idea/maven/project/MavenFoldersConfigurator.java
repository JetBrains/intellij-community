package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.maven.model.Resource;
import org.jetbrains.idea.maven.core.util.Path;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;

public class MavenFoldersConfigurator {
  private MavenProjectModel myMavenProject;
  private MavenImporterSettings myPrefs;
  private RootModelAdapter myModel;

  public static void updateProjectExcludedFolders(final Project p) throws MavenException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Module m : ModuleManager.getInstance(p).getModules()) {
          updateModuleExcludedFolders(m);
        }
      }
    });
  }

  private static void updateModuleExcludedFolders(Module m) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(m.getProject());
    MavenImporterSettings settings = manager.getImporterSettings();

    MavenProjectModel project = manager.findProject(m);
    if (project == null) return;

    RootModelAdapter a = new RootModelAdapter(m);
    new MavenFoldersConfigurator(project, settings, a).configFoldersUnderTargetDir();
    a.commit();
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
        if (hasRegisteredSourceSubfolder(f)) continue;
        if (isAlreadyExcluded(f)) continue;
        myModel.addExcludedFolder(f.getPath());
      }
    }
  }

  private void addAllSubDirsAsSources(File dir) {
    for (File f : getChildren(dir)) {
      if (!f.isDirectory()) continue;
      if (hasRegisteredSourceSubfolder(f)) continue;
      myModel.addSourceFolder(f.getPath(), false);
    }
  }

  private boolean hasRegisteredSourceSubfolder(File f) {
    String path = new Path(f.getPath()).getPath();
    for (Path existing : myModel.getSourceFolders()) {
      if (existing.getPath().startsWith(path)) return true;
    }
    return false;
  }

  private boolean isAlreadyExcluded(File f) {
    String path = new Path(f.getPath()).getPath();
    for (Path existing : myModel.getExcludedFolders()) {
      if (path.startsWith(existing.getPath())) return true;
    }
    return false;
  }

  private File[] getChildren(File dir) {
    File[] result = dir.listFiles();
    return result == null ? new File[0] : result;
  }
}
