package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.SelectBranchPopup;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

public class SvnIntegrateChangesActionPerformer implements SelectBranchPopup.BranchSelectedCallback {
  private final SvnVcs myVcs;
  private final MergerFactory myMergerFactory;

  private final SVNURL myCurrentBranch;

  public SvnIntegrateChangesActionPerformer(final Project project, final SVNURL currentBranchUrl, final MergerFactory mergerFactory) {
    myVcs = SvnVcs.getInstance(project);
    myCurrentBranch = currentBranchUrl;
    myMergerFactory = mergerFactory;
  }

  public void branchSelected(final Project project, final SvnBranchConfiguration configuration, final String url, final long revision) {
    if (myCurrentBranch.toString().equals(url)) {
      Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.source.and.target.same.text"), 
                               SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
      return;
    }

    final IntegratedSelectedOptionsDialog dialog = new IntegratedSelectedOptionsDialog(project, myCurrentBranch, url);
    dialog.show();

    if (dialog.isOK()) {
      ApplicationManager.getApplication().saveAll();
      dialog.saveOptions();

      final WorkingCopyInfo info = dialog.getSelectedWc();
      final File file = new File(info.getLocalPath());
      if ((! file.exists()) || (! file.isDirectory())) {
        Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.target.not.dir.text"),
                                 SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
        return;
      }

      final SVNURL sourceUrl = correctSourceUrl(info, url);

      if (sourceUrl == null) {
        Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.not.versioned.text"),
                                 SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
        return;
      }
      final SvnIntegrateChangesTask task = new SvnIntegrateChangesTask(myVcs, dialog.getSelectedWc(),
                myMergerFactory, sourceUrl);
      ProgressManager.getInstance().run(task);
    }
  }

  @Nullable
  private SVNURL correctSourceUrl(final WorkingCopyInfo info, final String targetUrl) {
    final SVNWCClient client = myVcs.createWCClient();
    try {
      final SVNInfo svnInfo = client.doInfo(new File(info.getLocalPath()), SVNRevision.WORKING);
      final String realTargetUrl = svnInfo.getURL().toString();

      if (realTargetUrl.length() > targetUrl.length()) {
        if (realTargetUrl.startsWith(targetUrl)) {
          return myCurrentBranch.appendPath(realTargetUrl.substring(targetUrl.length()), true);
        }
      } else if (realTargetUrl.equals(targetUrl)) {
        return myCurrentBranch;
      }
    }
    catch (SVNException e) {
      // tracked by return value
    }
    return null;
  }
}
