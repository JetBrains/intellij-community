package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.*;

/**
 * User: Vladislav.Kaznacheev
 */
public class DownloadArtifactsAction extends AnAction {
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);

    MavenImportToolWindow w = new MavenImportToolWindow(project, ProjectBundle.message("maven.downloading"));
    try {
      MavenArtifactDownloader.download(project);
    }
    catch (MavenException ex) {
      w.displayErrors(ex);
    }
    catch (CanceledException ex) {
    }
  }
}