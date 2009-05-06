package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import com.intellij.openapi.project.Project;

public class MavenProjectsProcessorResolvingTask extends MavenProjectsProcessorBasicTask {
  private boolean myQuickResolve;
  private MavenGeneralSettings myGeneralSettings;

  public MavenProjectsProcessorResolvingTask(boolean quickResolve, MavenProject project, MavenProjectsTree tree, MavenGeneralSettings generalSettings) {
    super(project, tree);
    myQuickResolve = quickResolve;
    myGeneralSettings = generalSettings;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator process)
    throws MavenProcessCanceledException {
    myTree.resolve(myMavenProject, myQuickResolve, myGeneralSettings, embeddersManager, console, process);
  }
}
