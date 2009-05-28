package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public class MavenProjectsProcessorArtifactsDownloadingTask extends MavenProjectsProcessorBasicTask {
  public MavenProjectsProcessorArtifactsDownloadingTask(MavenProject project,
                                                        MavenProjectsTree tree) {
    super(project, tree);
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator process)
    throws MavenProcessCanceledException {
    myTree.downloadArtifacts(myMavenProject, embeddersManager, console, process);
  }
}