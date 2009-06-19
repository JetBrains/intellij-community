package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import com.intellij.openapi.project.Project;

public class MavenProjectsProcessorFoldersResolvingTask extends MavenProjectsProcessorBasicTask {
  private MavenImportingSettings myImportingSettings;

  public MavenProjectsProcessorFoldersResolvingTask(MavenProject project, MavenImportingSettings importingSettings, MavenProjectsTree tree) {
    super(project, tree);
    myImportingSettings = importingSettings;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    myTree.resolveFolders(myMavenProject, myImportingSettings, embeddersManager, console, indicator);
  }
}
