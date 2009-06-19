package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import com.intellij.openapi.project.Project;

public class MavenProjectsProcessorResolvingTask extends MavenProjectsProcessorBasicTask {
  private MavenGeneralSettings myGeneralSettings;

  public MavenProjectsProcessorResolvingTask(
    MavenProject project,
    MavenProjectsTree tree,
    MavenGeneralSettings generalSettings) {
    super(project, tree);
    myGeneralSettings = generalSettings;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    myTree.resolve(myMavenProject, myGeneralSettings, embeddersManager, console, indicator);
  }
}
