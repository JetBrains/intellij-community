package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;

/**
 * @author max
 */
public class MoveChangesToAnotherListAction extends AnAction {
  public MoveChangesToAnotherListAction() {
    super("Move to another list", "Move selected changes to another changelist", IconLoader.getIcon("/actions/fileStatus.png"));
  }

  public void update(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    e.getPresentation().setEnabled(project != null && changes != null && changes.length > 0);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    if (changes == null) return;

    final ChangeListManager listManager = ChangeListManager.getInstance(project);
    ChangeListChooser chooser = new ChangeListChooser(project, listManager.getChangeLists(), null);
    chooser.show();
    ChangeList resultList = chooser.getSelectedList();
    if (resultList != null) {
      listManager.moveChangesTo(resultList, changes);
    }
  }
}
