package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import com.intellij.openapi.project.Project;

public interface MavenProjectsProcessorTask {
  void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProcess process) throws MavenProcessCanceledException;
}
