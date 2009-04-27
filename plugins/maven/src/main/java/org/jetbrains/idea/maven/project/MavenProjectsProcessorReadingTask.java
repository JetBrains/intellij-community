package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.embedder.MavenConsole;

import java.util.List;

public class MavenProjectsProcessorReadingTask implements MavenProjectsProcessorTask {
  private final Project myProject;
  private final MavenProjectsTree myTree;
  private volatile MavenGeneralSettings mySettings;
  private volatile List<VirtualFile> myFilesToUpdate;
  private volatile List<VirtualFile> myFilesToDelete;

  public MavenProjectsProcessorReadingTask(Project project, MavenProjectsTree tree, MavenGeneralSettings settings) {
    myProject = project;
    myTree = tree;
    mySettings = settings;
  }

  public MavenProjectsProcessorReadingTask(Project project,
                                                MavenProjectsTree tree,
                                                MavenGeneralSettings settings,
                                                List<VirtualFile> filesToUpdate,
                                                List<VirtualFile> filesToDelete) {
    this(project, tree, settings);
    myFilesToUpdate = filesToUpdate;
    myFilesToDelete = filesToDelete;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProcess process)
    throws MavenProcessCanceledException {
    if (myFilesToUpdate == null) {
      myTree.updateAll(mySettings, console, process);
    }
    else {
      myTree.delete(myFilesToDelete, mySettings, console, process);
      myTree.update(myFilesToUpdate, mySettings, console, process);
    }
  }

  public boolean immediateInTestMode() {
    return false;
  }
}