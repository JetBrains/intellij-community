package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;

/**
 * @author yole
 */
public class FilterCommittedAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      CommittedChangesPanel panel = ChangesViewContentManager.getInstance(project).getActiveComponent(CommittedChangesPanel.class);
      assert panel != null;
      panel.setChangesFilter();
    }
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      CommittedChangesPanel panel = ChangesViewContentManager.getInstance(project).getActiveComponent(CommittedChangesPanel.class);
      e.getPresentation().setVisible(panel != null && panel.getRepositoryLocation() != null);
    }
    else {
      e.getPresentation().setVisible(false);
    }
  }
}
