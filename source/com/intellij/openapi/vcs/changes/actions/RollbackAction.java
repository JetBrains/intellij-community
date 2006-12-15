/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:12:19
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.ui.Messages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RollbackAction extends AnAction {
  public void update(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    Change[] changes = e.getData(DataKeys.CHANGES);
    List<FilePath> missingFiles = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    List<VirtualFile> modifiedWithoutEditing = e.getData(ChangesListView.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    e.getPresentation().setEnabled(ChangesUtil.getChangeListIfOnlyOne(project, changes) != null ||
                                   (missingFiles != null && !missingFiles.isEmpty()) ||
                                   (modifiedWithoutEditing != null && !modifiedWithoutEditing.isEmpty()));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    List<FilePath> missingFiles = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    List<VirtualFile> modifiedWithoutEditing = e.getData(ChangesListView.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    if (missingFiles != null && !missingFiles.isEmpty()) {
      new RollbackDeletionAction().actionPerformed(e);
    }
    else if (modifiedWithoutEditing != null && !modifiedWithoutEditing.isEmpty()) {
      rollbackModifiedWithoutEditing(project, modifiedWithoutEditing);
    }
    else {
      Change[] changes = e.getData(DataKeys.CHANGES);
      final ChangeList list = ChangesUtil.getChangeListIfOnlyOne(project, changes);
      if (list == null) return;

      FileDocumentManager.getInstance().saveAllDocuments();
      RollbackChangesDialog.rollbackChanges(project, Arrays.asList(changes));
    }
  }

  private static void rollbackModifiedWithoutEditing(final Project project, final List<VirtualFile> modifiedWithoutEditing) {
    String message = (modifiedWithoutEditing.size() == 1)
      ? VcsBundle.message("rollback.modified.without.editing.confirm.single", modifiedWithoutEditing.get(0).getPresentableUrl())
      : VcsBundle.message("rollback.modified.without.editing.confirm.multiple", modifiedWithoutEditing.size());
    int rc = Messages.showYesNoDialog(project, message, VcsBundle.message("changes.action.rollback.title"), Messages.getQuestionIcon());
    if (rc != 0) {
      return;
    }
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    EditAction.editFiles(project, modifiedWithoutEditing, exceptions);
    if (exceptions.size() == 0) {
      final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
      changeListManager.ensureUpToDate(true);
      List<Change> changesToRollback = new ArrayList<Change>();
      for(VirtualFile virtualFile: modifiedWithoutEditing) {
        final Change change = changeListManager.getChange(virtualFile);
        if (change != null) {
          changesToRollback.add(change);
        }
      }
      if (changesToRollback.size() > 0) {
        ChangesUtil.processChangesByVcs(project, changesToRollback, new ChangesUtil.PerVcsProcessor<Change>() {
          public void process(final AbstractVcs vcs, final List<Change> items) {
            final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
            if (checkinEnvironment != null) {
              checkinEnvironment.rollbackChanges(items);
            }
          }
        });
        VirtualFileManager.getInstance().refresh(true, new Runnable() {
          public void run() {
            for(VirtualFile virtualFile: modifiedWithoutEditing) {
              VcsDirtyScopeManager.getInstance(project).fileDirty(virtualFile);
              FileStatusManager.getInstance(project).fileStatusChanged(virtualFile);
            }
          }
        });
      }
    }
  }
}