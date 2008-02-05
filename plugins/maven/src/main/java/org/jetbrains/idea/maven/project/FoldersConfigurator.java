package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.core.util.Path;

import java.util.Set;

public class FoldersConfigurator {
  private MavenProject myMavenProject;
  private MavenImporterSettings myPrefs;
  private RootModelAdapter myModel;
  private Set<Path> mySourceFolders = new HashSet<Path>();

  public FoldersConfigurator(MavenProject mavenProject,
                             MavenImporterSettings prefs,
                             RootModelAdapter model) {
    myMavenProject = mavenProject;
    myPrefs = prefs;
    myModel = model;
  }

  public void config() {
    configSourceFolders();
    configFoldersUnderTargetDir();
    configOutputFolders();
  }

  public void configSourceFolders() {
    for (Object o : myMavenProject.getCompileSourceRoots()) {
      addAndRegisterSourceFolder((String)o, false);
    }
    for (Object o : myMavenProject.getTestCompileSourceRoots()) {
      addAndRegisterSourceFolder((String)o, true);
    }

    for (Object o : myMavenProject.getResources()) {
      addAndRegisterSourceFolder(((Resource)o).getDirectory(), false);
    }
    for (Object o : myMavenProject.getTestResources()) {
      addAndRegisterSourceFolder(((Resource)o).getDirectory(), true);
    }
  }

  private void addAndRegisterSourceFolder(String path, boolean isTestSource) {
    myModel.addSourceDir(path, isTestSource);
    mySourceFolders.add(new Path(path));
  }

  private void configFoldersUnderTargetDir() {
    String path = myMavenProject.getBuild().getDirectory();
    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(path);
    if (dir == null) return;

    for (VirtualFile f : dir.getChildren()) {
      if (!f.isDirectory()) continue;
      if (FileUtil.pathsEqual(f.getName(), "generated-sources")) {
        addAllSubDirsAsSources(f);
      }
      else {
        if (mySourceFolders.contains(new Path(f.getPath()))) continue;
        myModel.excludeRoot(f.getPath());
      }
    }
  }

  private void addAllSubDirsAsSources(VirtualFile dir) {
    for (VirtualFile f : dir.getChildren()) {
      if (!f.isDirectory()) continue;
      myModel.addSourceDir(f.getPath(), false);
    }
  }

  private void configOutputFolders() {
    Build build = myMavenProject.getBuild();

    if (myPrefs.isUseMavenOutput()) {
      myModel.useModuleOutput(build.getOutputDirectory(), build.getTestOutputDirectory());
    }
    else {
      myModel.useProjectOutput();
      myModel.excludeRoot(build.getOutputDirectory());
      myModel.excludeRoot(build.getTestOutputDirectory());
    }
  }
}
