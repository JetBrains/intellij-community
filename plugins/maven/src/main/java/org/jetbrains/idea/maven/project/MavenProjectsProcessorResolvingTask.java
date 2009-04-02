package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import com.intellij.openapi.project.Project;

public class MavenProjectsProcessorResolvingTask extends MavenProjectsProcessorBasicTask {
  public MavenProjectsProcessorResolvingTask(MavenProject project, MavenProjectsTree tree) {
    super(project, tree);
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProcess process)
    throws MavenProcessCanceledException {
    myTree.resolve(myMavenProject, embeddersManager, console, process);
  }
}
