package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;

import java.util.Arrays;

/**
 * @author yole
*/
public class RollbackDialogAction extends AnAction {
  public RollbackDialogAction() {
    super(VcsBundle.message("changes.action.rollback.text"), VcsBundle.message("changes.action.rollback.description"),
          IconLoader.getIcon("/actions/rollback.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    ChangesBrowser browser = e.getData(ChangesBrowser.DATA_KEY);
    RollbackChangesDialog.rollbackChanges(project, Arrays.asList(changes), true);
    ChangeListManager.getInstance(project).ensureUpToDate(false);
    if (browser != null) {
      browser.rebuildList();
    }
  }

  public void update(AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    e.getPresentation().setEnabled(changes != null);
  }
}