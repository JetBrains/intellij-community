package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import com.intellij.openapi.project.Project;

public class MavenProjectsProcessorPluginDownloadingTask extends MavenProjectsProcessorBasicTask {
  private org.apache.maven.project.MavenProject myNativeMavenProject;

  public MavenProjectsProcessorPluginDownloadingTask(MavenProject project,
                                                     org.apache.maven.project.MavenProject nativeMavenProject,
                                                     MavenProjectsTree tree) {
    super(project, tree);
    myNativeMavenProject = nativeMavenProject;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProcess process)
    throws MavenProcessCanceledException {
    myTree.downloadPlugins(myMavenProject, myNativeMavenProject, embeddersManager, console, process);
  }
}