package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.MavenImportProcessor;
import org.jetbrains.idea.maven.project.MavenException;
import org.jetbrains.idea.maven.project.CanceledException;

/**
 * User: Vladislav.Kaznacheev
 */
public class SynchronizeWithMavenAction extends AnAction {
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      try {
        new MavenImportProcessor(project).synchronize(true);
      }
      catch (MavenException e1) {
        // TODO temporary catch block
        throw new RuntimeException(e1);
      }
      catch (CanceledException e1) {
      }
    }
  }
}
