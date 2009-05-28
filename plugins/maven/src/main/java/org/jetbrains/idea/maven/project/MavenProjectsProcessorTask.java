package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public interface MavenProjectsProcessorTask {
  void perform(Project project,
               MavenEmbeddersManager embeddersManager,
               MavenConsole console,
               MavenProgressIndicator process) throws MavenProcessCanceledException;
}
