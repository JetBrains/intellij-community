package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class ShowDiffWithLocalAction extends AnAction {
  public ShowDiffWithLocalAction() {
    super(VcsBundle.message("show.diff.with.local.action.text"),
          VcsBundle.message("show.diff.with.local.action.description"),
          IconLoader.getIcon("/actions/diffWithCurrent.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    Change[] changes = e.getData(DataKeys.CHANGES);
    assert changes != null;
    List<Change> changesToLocal = new ArrayList<Change>();
    for(Change change: changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      if (isValidAfterRevision(afterRevision)) {
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
    e.getPresentation().setEnabled(project != null && changes != null && anyHasAfterRevision(changes));
  }

  private static boolean isValidAfterRevision(final ContentRevision afterRevision) {
    return afterRevision != null && !afterRevision.getFile().isNonLocal() && !afterRevision.getFile().isDirectory();
  }

  private static boolean anyHasAfterRevision(final Change[] changes) {
    for(Change c: changes) {
      if (isValidAfterRevision(c.getAfterRevision())) {
        return true;
      }
    }
    return false;
  }
}