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
  private final Object myMessage;

  public MavenProjectsProcessorReadingTask(boolean force, MavenProjectsTree tree, MavenGeneralSettings settings, Object message) {
    this(null, null, force, tree, settings, message);
  }

  public MavenProjectsProcessorReadingTask(List<VirtualFile> filesToUpdate,
                                           List<VirtualFile> filesToDelete,
                                           boolean force,
                                           MavenProjectsTree tree,
                                           MavenGeneralSettings settings,
                                           Object message) {
    myForce = force;
    myTree = tree;
    mySettings = settings;
    myFilesToUpdate = filesToUpdate;
    myFilesToDelete = filesToDelete;
    myMessage = message;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    if (myFilesToUpdate == null) {
      myTree.updateAll(myForce, mySettings, indicator, myMessage);
    }
    else {
      myTree.delete(myFilesToDelete, mySettings, indicator, myMessage);
      myTree.update(myFilesToUpdate, myForce, mySettings, indicator, myMessage);
    }
  }
}