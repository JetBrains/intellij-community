package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class FilterCommittedAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      CommittedChangesPanel panel = CommittedChangesViewManager.getInstance(project).getActivePanel();
      assert panel != null;
      panel.setChangesFilter();
    }
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      CommittedChangesPanel panel = CommittedChangesViewManager.getInstance(project).getActivePanel();
      e.getPresentation().setEnabled(panel != null);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }
}
