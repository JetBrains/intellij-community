package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.Path;

import java.io.File;
import java.util.Set;

public class FoldersConfigurator {
  private MavenProject myMavenProject;
  private MavenImporterSettings myPrefs;
  private RootModelAdapter myModel;
  private Set<Path> mySourceFolders = new HashSet<Path>();

  public static void updateProjectExcludedFolders(final Project p) throws MavenException {
    try {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            doUpdateProjectsExcludeFolders(p);
          }
          catch (MavenException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (RuntimeException e) {
      if (e.getCause() instanceof MavenException) {
        throw (MavenException)e.getCause();
      }
    }
  }

  private static void doUpdateProjectsExcludeFolders(Project p) throws MavenException {
    MavenEmbedder embedder = MavenCore.getInstance(p).getState().createEmbedder();
    try {
      MavenProjectReader r = new MavenProjectReader(embedder);
      for (Module m : ModuleManager.getInstance(p).getModules()) {
        updateModuleExcludedFolders(m, r);
      }
    }
    finally {
      MavenEnv.releaseEmbedder(embedder);
    }
  }

  private static void updateModuleExcludedFolders(Module m, MavenProjectReader r) throws MavenException {
    MavenImporter importer = MavenImporter.getInstance(m.getProject());
    MavenImporterSettings settings = importer.getImporterSettings();

    VirtualFile f = importer.findPomForModule(m);
    if (f == null) return;
    MavenProject project = r.readBare(f.getPath());

    RootModelAdapter a = new RootModelAdapter(m);
    new FoldersConfigurator(project, settings, a).updateFodersUnderTargetDir();
    a.commit();
  }

  public FoldersConfigurator(MavenProject mavenProject, MavenImporterSettings settings, RootModelAdapter model) {
    myMavenProject = mavenProject;
    myPrefs = settings;
    myModel = model;
  }

  public void config() {
    configSourceFolders();
    configFoldersUnderTargetDir();
    configOutputFolders();
  }

  private void configSourceFolders() {
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
    File targetDir = new File(myMavenProject.getBuild().getDirectory());

    for (File f : getChildren(targetDir)) {
      if (!f.isDirectory()) continue;

      if (FileUtil.pathsEqual(f.getName(), "generated-sources")) {
        addAllSubDirsAsSources(f);
      }
      else {
        if (hasRegisteredSubfolder(f)) continue;
        myModel.excludeRoot(f.getPath());
      }
    }
  }

  private void addAllSubDirsAsSources(File dir) {
    for (File f : getChildren(dir)) {
      if (!f.isDirectory()) continue;
      if (hasRegisteredSubfolder(f)) continue;
      myModel.addSourceDir(f.getPath(), false);
    }
  }

  private boolean hasRegisteredSubfolder(File f) {
    for (Path existing : mySourceFolders) {
      if (existing.getPath().startsWith(new Path(f.getPath()).getPath())) return true;
    }
    return false;
  }

  private File[] getChildren(File dir) {
    File[] result = dir.listFiles();
    return result == null ? new File[0] : result;
  }

  private void updateFodersUnderTargetDir() {
    collectExistingSourceFolders();
    configFoldersUnderTargetDir();
  }

  private void collectExistingSourceFolders() {
    mySourceFolders = myModel.getExistingSourceFolders();
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
