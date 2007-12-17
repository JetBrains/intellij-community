package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.CanceledException;
import org.jetbrains.idea.maven.project.MavenException;
import org.jetbrains.idea.maven.project.MavenImportProcessor;
import org.jetbrains.idea.maven.project.MavenImporter;

public class SynchronizeWithMavenAction extends AnAction {
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);

    try {
      new MavenImportProcessor(project).synchronize();
    }
    catch (MavenException ex) {
      project.getComponent(MavenImporter.class).displayImportErrors(ex);
    }
    catch (CanceledException ex) {
    }
  }
}
