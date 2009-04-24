package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import com.intellij.openapi.project.Project;

public class MavenProjectsProcessorArtifactsDownloadingTask extends MavenProjectsProcessorBasicTask {
  private MavenDownloadingSettings myDownloadingSettings;

  public MavenProjectsProcessorArtifactsDownloadingTask(MavenProject project,
                                               MavenProjectsTree tree,
                                               MavenDownloadingSettings downloadingSettings) {
    super(project, tree);
    myDownloadingSettings = downloadingSettings;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProcess process)
    throws MavenProcessCanceledException {
    myTree.downloadArtifacts(myMavenProject, myDownloadingSettings, embeddersManager, console, process);
  }
}