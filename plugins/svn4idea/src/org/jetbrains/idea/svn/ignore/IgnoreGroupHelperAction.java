package org.jetbrains.idea.svn.ignore;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.BasicAction;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;

public class IgnoreGroupHelperAction extends BasicAction {
  private boolean myAllCanBeIgnored;
  private boolean myAllAreIgnored;
  private FileIterationListener myListener;

  protected String getActionName(final AbstractVcs vcs) {
    return null;
  }

  public void update(final AnActionEvent e) {
    myAllAreIgnored = true;
    myAllCanBeIgnored = true;

    super.update(e);
  }

  public void setFileIterationListener(final FileIterationListener listener) {
    myListener = listener;
  }

  private boolean isEnabledImpl(final SvnVcs vcs, final VirtualFile file) {
    final SVNStatusType fileStatus = getApproximateStatus(vcs, file);

    if (fileStatus == SVNStatusType.STATUS_IGNORED) {
      myAllCanBeIgnored = false;
      return myAllAreIgnored | myAllCanBeIgnored;
    } else if ((fileStatus == null) || (fileStatus == SVNStatusType.STATUS_UNVERSIONED)) {
      // check parent
      final VirtualFile parent = file.getParent();
      if (parent != null) {
        final SVNStatusType parentStatus = getApproximateStatus(vcs, parent);
        if ((parentStatus != null) && (parentStatus != SVNStatusType.STATUS_IGNORED) &&
        (parentStatus != SVNStatusType.STATUS_OBSTRUCTED) && (parentStatus != SVNStatusType.STATUS_UNVERSIONED)) {
          myAllAreIgnored = false;
          return myAllAreIgnored | myAllCanBeIgnored;
        }
      }
    }
    myAllCanBeIgnored = false;
    myAllAreIgnored = false;
    return false;
  }

  protected boolean isEnabled(final Project project, final SvnVcs vcs, final VirtualFile file) {
    final boolean result = isEnabledImpl(vcs, file);
    if (result) {
      myListener.onFileEnabled(file);
    }
    return result;
  }

  public boolean allCanBeIgnored() {
    return myAllCanBeIgnored;
  }

  public boolean allAreIgnored() {
    return myAllAreIgnored;
  }

  @Nullable
  private SVNStatusType getApproximateStatus(final SvnVcs vcs, final VirtualFile file) {
    SvnVcs.SVNStatusHolder cachedStatus = vcs.getCachedStatus(file);
    SVNStatus status;
    try {
      if (cachedStatus != null) {
        status = cachedStatus.getStatus();
      } else {
        SVNStatusClient stClient = vcs.createStatusClient();
        status = stClient.doStatus(new File(file.getPath()), false);
        vcs.cacheStatus(file, status);
      }
      return (status == null) ? null : status.getContentsStatus();
    }
    catch (SVNException e) {
      return null;
    }
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(final Project project, final SvnVcs activeVcs, final VirtualFile file, final DataContext context)
      throws VcsException {

  }

  protected void batchPerform(final Project project, final SvnVcs activeVcs, final VirtualFile[] file, final DataContext context)
      throws VcsException {

  }

  protected boolean isBatchAction() {
    return false;
  }
}
