package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.ShareDialog;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;

public class ShareProjectAction extends BasicAction {

  protected String getActionName(AbstractVcs vcs) {
    return "Share Directory...";
  }


  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] files = (VirtualFile[])dataContext.getData(DataConstants.VIRTUAL_FILE_ARRAY);
    if (files == null || files.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    boolean enabled = false;
    boolean visible = false;
    if (files.length == 1) {
      VirtualFile file = files[0];
      VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
      VirtualFile root = null;

      for (VirtualFile root1 : roots) {
        if (root1.equals(file)) {
          root = file;
          break;
        }
      }
      if (root != null && root.isDirectory()) {
        visible = true;
        if (!SVNWCUtil.isVersionedDirectory(new File(root.getPath()))) {
          enabled = true;
        }
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

  protected void perform(Project project, final SvnVcs activeVcs, final VirtualFile file, DataContext context, AbstractVcsHelper helper) throws VcsException {
    ShareDialog shareDialog = new ShareDialog(project);
    shareDialog.show();

    final String parent = shareDialog.getSelectedURL();
    if (shareDialog.isOK() && parent != null) {
      final SVNException[] error = new SVNException[1];
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {

        public void run() {
          try {
            SVNURL url = SVNURL.parseURIEncoded(parent).appendPath(file.getName(), false);
            SVNCommitInfo info = activeVcs.createCommitClient().doMkDir(new SVNURL[] {url}, "Directory '" + file.getName() +"' created by IntelliJ IDEA");
            SVNRevision revision = SVNRevision.create(info.getNewRevision());
            activeVcs.createUpdateClient().doCheckout(url, new File(file.getPath()), SVNRevision.UNDEFINED, revision, true);
            activeVcs.createWCClient().doAdd(new File(file.getPath()), true, false, false, true);
          } catch (SVNException e) {
            error[0] = e;
          }
        }
      }, "Share Directory", false, project);
      if (error[0] != null) {
        throw new VcsException(error[0].getMessage());
      }
      Messages.showInfoMessage(project, "To complete share operation commit '" + file.getName() + "'.", "Share Directory");
    }

  }

  protected void batchPerform(Project project, final SvnVcs activeVcs, VirtualFile[] file, DataContext context, AbstractVcsHelper helper) throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
