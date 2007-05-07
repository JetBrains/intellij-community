package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.project.Project;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class ShowDiffWithLocalAction extends AnAction {
  public ShowDiffWithLocalAction() {
    super("Show Diff with Local", "Compare selected revision with the local version of the file",
          IconLoader.getIcon("/actions/diffWithCurrent.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    Change[] changes = e.getData(DataKeys.CHANGES);
    assert changes != null;
    List<Change> changesToLocal = new ArrayList<Change>();
    for(Change change: changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null && !afterRevision.getFile().isNonLocal() && !afterRevision.getFile().isDirectory()) {
        changesToLocal.add(new Change(afterRevision, CurrentContentRevision.create(afterRevision.getFile())));
      }
    }
    if (!changesToLocal.isEmpty()) {
      Change[] changeArray = changesToLocal.toArray(new Change[changesToLocal.size()]);
      ShowDiffAction.showDiffForChange(changeArray, 0, project);
    }
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    Change[] changes = e.getData(DataKeys.CHANGES);
    e.getPresentation().setEnabled(project != null && changes != null);
  }
}