package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.impl.VcsBlockHistoryDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsSelectionUtil;

public class SelectedBlockHistoryAction extends AbstractVcsAction {

  protected boolean isEnabled(VcsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    if (selectedFiles == null) return false;
    if (selectedFiles.length == 0) return false;
    VirtualFile file = selectedFiles[0];
    Project project = context.getProject();
    if (project == null) return false;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = vcs.getVcsBlockHistoryProvider();
    if (vcsHistoryProvider == null) return false;
    if (!vcs.fileExistsInVcs(new FilePathImpl(file))) return false;

    VcsSelection selection = VcsSelectionUtil.getSelection(context);
    if (selection == null) {
      return false;
    }
    return true;
  }

  public void actionPerformed(VcsContext context) {
    try {
      VcsSelection selection = VcsSelectionUtil.getSelection(context);
      VirtualFile file = FileDocumentManager.getInstance().getFile(selection.getDocument());
      Project project = context.getProject();
      AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      if (activeVcs == null) return;

      VcsHistoryProvider provider = activeVcs.getVcsBlockHistoryProvider();

      int selectionStart = selection.getSelectionStartLineNumber();
      int selectionEnd = selection.getSelectionEndLineNumber();
      VcsHistorySession session = provider.createSessionFor(new FilePathImpl(file));
      if (session == null) return;
      VcsBlockHistoryDialog vcsHistoryDialog =
        new VcsBlockHistoryDialog(project,
                                  context.getSelectedFiles()[0],
                                  activeVcs,
                                  provider,
                                  session,
                                  Math.min(selectionStart, selectionEnd),
                                  Math.max(selectionStart, selectionEnd),
                                  selection.getDialogTitle());

      vcsHistoryDialog.show();
    }
    catch (Exception exception) {
      reportError(exception);
    }

  }

  protected void update(VcsContext context, Presentation presentation) {
    presentation.setEnabled(isEnabled(context));
    VcsSelection selection = VcsSelectionUtil.getSelection(context);
    if (selection != null) {
      presentation.setText(selection.getActionName());
    }
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected static void reportError(Exception exception) {
    exception.printStackTrace();
    Messages.showMessageDialog(exception.getLocalizedMessage(), VcsBundle.message("message.title.could.not.load.file.history"), Messages.getErrorIcon());
  }
}
