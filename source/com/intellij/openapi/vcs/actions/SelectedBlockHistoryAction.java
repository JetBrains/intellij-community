package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.impl.VcsBlockHistoryDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsUtil;

public class SelectedBlockHistoryAction extends FileHistoryAction {

  protected boolean isEnabled(VcsContext context) {

    if (!super.isEnabled(context)) return false;

    VcsSelection selection = VcsUtil.getSelection(context);
    if (selection == null) {
      return false;
    }

    VirtualFile file = FileDocumentManager.getInstance().getFile(selection.getDocument());
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(context.getProject()).getVcsFor(file);
    if (activeVcs == null) return false;

    VcsHistoryProvider provider = getProvider(activeVcs);
    if (provider == null) return false;

    return activeVcs.fileExistsInVcs(new FilePathImpl(file));
  }

  public void actionPerformed(VcsContext context) {
    try {
      VcsSelection selection = VcsUtil.getSelection(context);
      VirtualFile file = FileDocumentManager.getInstance().getFile(selection.getDocument());
      Project project = context.getProject();
      AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      if (activeVcs == null) return;

      VcsHistoryProvider provider = getProvider(activeVcs);

      int selectionStart = selection.getSelectionStartLineNumber();
      int selectionEnd = selection.getSelectionEndLineNumber();
      VcsHistorySession session = provider.createSessionFor(new FilePathImpl(file));
      VcsBlockHistoryDialog vcsHistoryDialog =
        new VcsBlockHistoryDialog(project,
                                  context.getSelectedFiles()[0],
                                  activeVcs,
                                  provider,
                                  session,
                                  Math.min(selectionStart, selectionEnd),
                                  Math.max(selectionStart, selectionEnd));

      vcsHistoryDialog.show();
    }
    catch (Exception exception) {
      reportError(exception);
    }

  }

  protected VcsHistoryProvider getProvider(AbstractVcs activeVcs) {
    return activeVcs.getVcsBlockHistoryProvider();
  }
}
