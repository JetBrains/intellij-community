package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RelocateDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import java.io.File;

/**
 * @author yole
 */
public class RelocateAction extends BasicAction {
  protected String getActionName(final AbstractVcs vcs) {
    return "Relocate working copy to a different URL";
  }

  protected boolean isEnabled(final Project project, final SvnVcs vcs, final VirtualFile file) {
    SVNInfo info = vcs.getInfoWithCaching(file);
    return info != null && info.getURL() != null;
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(final Project project, final SvnVcs activeVcs, final VirtualFile file, DataContext context) throws VcsException {
    SVNInfo info = activeVcs.getInfoWithCaching(file);
    assert info != null;
    RelocateDialog dlg = new RelocateDialog(project, info.getURL());
    dlg.show();
    if (!dlg.isOK()) return;
    final String beforeURL = dlg.getBeforeURL();
    final String afterURL = dlg.getAfterURL();
    if (beforeURL.equals(afterURL)) return;
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setIndeterminate(true);
        }
        final SVNUpdateClient client = activeVcs.createUpdateClient();
        try {
          client.doRelocate(new File(file.getPath()),
                            SVNURL.parseURIEncoded(beforeURL),
                            SVNURL.parseURIEncoded(afterURL),
                            true);
          VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
        }
        catch (final SVNException e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog(project, "Error relocating working copy: " + e.getMessage(), "Relocate Working Copy");
            }
          });
        }
      }
    }, "Relocating Working Copy", false, project);
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] file, DataContext context) throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}