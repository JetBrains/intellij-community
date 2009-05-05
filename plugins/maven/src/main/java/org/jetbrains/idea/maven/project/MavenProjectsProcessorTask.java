package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import com.intellij.openapi.project.Project;

public interface MavenProjectsProcessorTask {
  void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator process) throws
                                                                                                                              MavenProcessCanceledException;
}
