package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public class MavenProjectsProcessorFoldersResolvingTask extends MavenProjectsProcessorBasicTask {
  private final MavenImportingSettings myImportingSettings;
  private final Object myMessage;

  public MavenProjectsProcessorFoldersResolvingTask(MavenProject project,
                                                    MavenImportingSettings importingSettings,
                                                    MavenProjectsTree tree,
                                                    Object message) {
    super(project, tree);
    myImportingSettings = importingSettings;
    myMessage = message;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    myTree.resolveFolders(myMavenProject, myImportingSettings, embeddersManager, console, indicator, myMessage);
  }
}
