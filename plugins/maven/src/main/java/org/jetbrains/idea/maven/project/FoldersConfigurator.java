package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.util.MavenFactory;
import org.jetbrains.idea.maven.core.util.Path;

import java.io.File;

public class FoldersConfigurator {
  private MavenProject myMavenProject;
  private MavenImporterSettings myPrefs;
  private RootModelAdapter myModel;

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
    MavenEmbedder embedder = MavenFactory.createEmbedder(MavenCore.getInstance(p).getState());
    try {
      MavenProjectReader r = new MavenProjectReader(embedder);
      for (Module m : ModuleManager.getInstance(p).getModules()) {
        updateModuleExcludedFolders(m, r);
      }
    }
    finally {
      MavenFactory.releaseEmbedder(embedder);
    }
  }

  private static void updateModuleExcludedFolders(Module m, MavenProjectReader r) throws MavenException {
    MavenImporter importer = MavenImporter.getInstance(m.getProject());
    MavenImporterSettings settings = importer.getImporterSettings();

    VirtualFile f = importer.findPomForModule(m);
    if (f == null) return;
    MavenProject project = r.readBare(f.getPath());

    RootModelAdapter a = new RootModelAdapter(m);
    new FoldersConfigurator(project, settings, a).configFoldersUnderTargetDir();
    a.commit();
  }

  public FoldersConfigurator(MavenProject mavenProject, MavenImporterSettings settings, RootModelAdapter model) {
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
    for (Object o : myMavenProject.getCompileSourceRoots()) {
      myModel.addSourceFolder((String)o, false);
    }
    for (Object o : myMavenProject.getTestCompileSourceRoots()) {
      myModel.addSourceFolder((String)o, true);
    }

    for (Object o : myMavenProject.getResources()) {
      myModel.addSourceFolder(((Resource)o).getDirectory(), false);
    }
    for (Object o : myMavenProject.getTestResources()) {
      myModel.addSourceFolder(((Resource)o).getDirectory(), true);
    }
  }

  private void configOutputFolders() {
    Build build = myMavenProject.getBuild();

    if (myPrefs.isUseMavenOutput()) {
      myModel.useModuleOutput(build.getOutputDirectory(), build.getTestOutputDirectory());
    }
    else {
      myModel.useProjectOutput();
      myModel.addExcludedFolder(build.getOutputDirectory());
      myModel.addExcludedFolder(build.getTestOutputDirectory());
    }
  }

  private void configFoldersUnderTargetDir() {
    File targetDir = new File(myMavenProject.getBuild().getDirectory());

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
