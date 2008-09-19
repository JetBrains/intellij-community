package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnWorkingCopyFormatHolder;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.dialogs.ShareDialog;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

public class ShareProjectAction extends BasicAction {

  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("share.directory.action");
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if ((project == null) || (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning())) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (files == null || files.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    boolean enabled = false;
    boolean visible = false;
    if (files.length == 1 && files [0].isDirectory()) {
      visible = true;
      if (!SVNWCUtil.isVersionedDirectory(new File(files [0].getPath()))) {
        enabled = true;
      }
    }
    presentation.setEnabled(enabled);
    presentation.setVisible(visible);
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    return false;
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(final Project project, final SvnVcs activeVcs, final VirtualFile file, DataContext context) throws VcsException {
    ShareDialog shareDialog = new ShareDialog(project);
    shareDialog.show();

    final String parent = shareDialog.getSelectedURL();
    if (shareDialog.isOK() && parent != null) {
      final Ref<Boolean> actionStarted = new Ref<Boolean>(Boolean.TRUE);
      final SVNException[] error = new SVNException[1];

      ExclusiveBackgroundVcsAction.run(project, new Runnable() {
        public void run() {
          ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
            public void run() {
              try {
                final File path = new File(file.getPath());
                if (! SvnCheckoutProvider.promptForWCopyFormat(path, project)) {
                  // action cancelled
                  actionStarted.set(Boolean.FALSE);
                  return;
                }
                SVNURL url = SVNURL.parseURIEncoded(parent).appendPath(file.getName(), false);
                SVNCommitInfo info = activeVcs.createCommitClient().doMkDir(new SVNURL[] {url},
                                                                            SvnBundle.message("share.directory.commit.message", file.getName(),
                                                                                              ApplicationNamesInfo.getInstance().getFullProductName()));
                SVNRevision revision = SVNRevision.create(info.getNewRevision());
                activeVcs.createUpdateClient().doCheckout(url, path, SVNRevision.UNDEFINED, revision, true);
                SvnWorkingCopyFormatHolder.setPresetFormat(null);

                addRecursively(activeVcs, file);
              } catch (SVNException e) {
                error[0] = e;
              } finally {
                SvnWorkingCopyFormatHolder.setPresetFormat(null);
              }
            }
          }, SvnBundle.message("share.directory.title"), false, project);
        }
      });

      if (Boolean.TRUE.equals(actionStarted.get())) {
        if (error[0] != null) {
          throw new VcsException(error[0].getMessage());
        }
        Messages.showInfoMessage(project, SvnBundle.message("share.directory.info.message", file.getName()),
                                 SvnBundle.message("share.directory.title"));
      }
    }
  }

  @Override
  protected void doVcsRefresh(final Project project, final VirtualFile file) {
    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file);
  }

  private void addRecursively(final SvnVcs activeVcs, final VirtualFile file) throws SVNException {
    final SVNWCClient wcClient = activeVcs.createWCClient();
    final SvnExcludingIgnoredOperation operation = new SvnExcludingIgnoredOperation(activeVcs.getProject(), new SvnExcludingIgnoredOperation.Operation() {
      public void doOperation(final VirtualFile virtualFile) throws SVNException {
        final File ioFile = new File(virtualFile.getPath());
        wcClient.doAdd(ioFile, true, false, false, SVNDepth.EMPTY, false, false);
      }
    }, SVNDepth.INFINITY);

    operation.execute(file);
  }

  protected void batchPerform(Project project, final SvnVcs activeVcs, VirtualFile[] file, DataContext context) throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
