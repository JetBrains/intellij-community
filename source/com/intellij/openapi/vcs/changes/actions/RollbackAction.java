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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RollbackAction extends AnAction {
  public void update(AnActionEvent e) {
    final boolean isEnabled = isEnabled(e);
    e.getPresentation().setEnabled(isEnabled);
    if (isEnabled) {
      VirtualFile[] files = e.getData(DataKeys.VIRTUAL_FILE_ARRAY);
      if (files != null) {
        for(VirtualFile file: files) {
          final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(e.getData(DataKeys.PROJECT)).getVcsFor(file);
          if (vcs != null) {
            final RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
            if (rollbackEnvironment != null) {
              e.getPresentation().setText(rollbackEnvironment.getRollbackOperationName());
            }
          }
        }
      }
    }
  }

  private static boolean isEnabled(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    Change[] changes = getChanges(project, e);
    if (changes != null && changes.length > 0) {
      return true;
    }
    List<FilePath> missingFiles = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    if (missingFiles != null && !missingFiles.isEmpty()) {
      return true;
    }
    List<VirtualFile> modifiedWithoutEditing = getModifiedWithoutEditing(e);
    if (modifiedWithoutEditing != null && !modifiedWithoutEditing.isEmpty()) {
      return true;
    }
    return false;
  }

  public void actionPerformed(AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    Project project = e.getData(DataKeys.PROJECT);
    List<FilePath> missingFiles = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    if (missingFiles != null && !missingFiles.isEmpty()) {
      new RollbackDeletionAction().actionPerformed(e);
    }
    else {
      List<VirtualFile> modifiedWithoutEditing = getModifiedWithoutEditing(e);
      if (modifiedWithoutEditing != null && !modifiedWithoutEditing.isEmpty()) {
        rollbackModifiedWithoutEditing(project, modifiedWithoutEditing);
      }
      else {
        Change[] changes = getChanges(project, e);
        if (changes != null) {
          RollbackChangesDialog.rollbackChanges(project, Arrays.asList(changes));
        }
      }
    }
  }

  @Nullable
  private static Change[] getChanges(final Project project, final AnActionEvent e) {
    final Change[] changes = e.getData(DataKeys.CHANGES);
    if (changes != null && changes.length > 0) {
      if (ChangesUtil.getChangeListIfOnlyOne(project, changes) == null) {
        return null;
      }
      return changes;
    }
    final VirtualFile[] virtualFiles = e.getData(DataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length > 0) {
      List<Change> result = new ArrayList<Change>();
      for(VirtualFile file: virtualFiles) {
        result.addAll(ChangeListManager.getInstance(project).getChangesIn(file));
      }
      return result.toArray(new Change[result.size()]);
    }
    return null;
  }

  @Nullable
  private static List<VirtualFile> getModifiedWithoutEditing(final AnActionEvent e) {
    final List<VirtualFile> modifiedWithoutEditing = e.getData(ChangesListView.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    if (modifiedWithoutEditing != null && modifiedWithoutEditing.size() > 0) {
      return modifiedWithoutEditing;
    }
    return null;
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
    ChangesUtil.processVirtualFilesByVcs(project, modifiedWithoutEditing, new ChangesUtil.PerVcsProcessor<VirtualFile>() {
      public void process(final AbstractVcs vcs, final List<VirtualFile> items) {
        final RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
        if (rollbackEnvironment != null) {
          exceptions.addAll(rollbackEnvironment.rollbackModifiedWithoutCheckout(items));
        }
      }
    });
    if (!exceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("rollback.modified.without.checkout.error.tab"));
    }
    VirtualFileManager.getInstance().refresh(true, new Runnable() {
      public void run() {
        for(VirtualFile virtualFile: modifiedWithoutEditing) {
          VcsDirtyScopeManager.getInstance(project).fileDirty(virtualFile);
        }
      }
    });
  }
}