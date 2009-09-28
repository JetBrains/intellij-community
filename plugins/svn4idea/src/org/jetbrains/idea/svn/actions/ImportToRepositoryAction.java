package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.idea.svn.dialogs.ImportDialog;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 08.07.2005
 * Time: 21:44:21
 * To change this template use File | Settings | File Templates.
 */
public class ImportToRepositoryAction extends AnAction {
  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(presentation.isEnabled() &&
      (! ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    ImportDialog dialog = new ImportDialog(project);
    dialog.show();
  }
}
