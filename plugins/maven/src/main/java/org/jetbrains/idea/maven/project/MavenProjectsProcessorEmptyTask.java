package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import com.intellij.openapi.project.Project;

public class MavenProjectsProcessorEmptyTask extends MavenProjectsProcessorBasicTask {
  public MavenProjectsProcessorEmptyTask(MavenProject project) {
    super(project, null);
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MavenProjectsProcessorBasicTask
           && myMavenProject.equals(((MavenProjectsProcessorBasicTask)o).myMavenProject);
  }
}
