package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public class MavenProjectsProcessorResolvingTask extends MavenProjectsProcessorBasicTask {
  private final MavenGeneralSettings myGeneralSettings;
  private final Object myMessage;

  public MavenProjectsProcessorResolvingTask(MavenProject project,
                                             MavenProjectsTree tree,
                                             MavenGeneralSettings generalSettings,
                                             Object message) {
    super(project, tree);
    myGeneralSettings = generalSettings;
    myMessage = message;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    myTree.resolve(myMavenProject, myGeneralSettings, embeddersManager, console, indicator, myMessage);
  }
}
