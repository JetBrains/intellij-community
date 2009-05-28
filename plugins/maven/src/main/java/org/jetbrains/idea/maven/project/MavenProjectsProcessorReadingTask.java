package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.List;

public class MavenProjectsProcessorReadingTask implements MavenProjectsProcessorTask {
  private final boolean myForce;
  private final MavenProjectsTree myTree;
  private final MavenGeneralSettings mySettings;
  private final List<VirtualFile> myFilesToUpdate;
  private final List<VirtualFile> myFilesToDelete;

  public MavenProjectsProcessorReadingTask(boolean force, MavenProjectsTree tree, MavenGeneralSettings settings) {
    this(null, null, force, tree, settings);
  }

  public MavenProjectsProcessorReadingTask(List<VirtualFile> filesToUpdate,
                                           List<VirtualFile> filesToDelete,
                                           boolean force,
                                           MavenProjectsTree tree,
                                           MavenGeneralSettings settings) {
    myForce = force;
    myTree = tree;
    mySettings = settings;
    myFilesToUpdate = filesToUpdate;
    myFilesToDelete = filesToDelete;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator process)
    throws MavenProcessCanceledException {
    if (myFilesToUpdate == null) {
      myTree.updateAll(myForce, mySettings, process);
    }
    else {
      myTree.delete(myFilesToDelete, mySettings, process);
      myTree.update(myFilesToUpdate, myForce, mySettings, process);
    }
  }
}