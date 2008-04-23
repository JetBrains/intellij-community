package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

public class CopyEventHandler implements ISVNEventHandler {
  private ProgressIndicator myProgress;

  public CopyEventHandler(ProgressIndicator progress) {
    myProgress = progress;
  }

  public void handleEvent(SVNEvent event, double p) {
    String path = event.getFile() != null ? event.getFile().getName() : event.getPath();
    if (path == null) {
      return;
    }
    if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
      myProgress.setText2(SvnBundle.message("progress.text2.adding", path));
    }
    else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
      myProgress.setText2(SvnBundle.message("progress.text2.deleting", path));
    }
    else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
      myProgress.setText2(SvnBundle.message("progress.text2.sending", path));
    }
    else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
      myProgress.setText2(SvnBundle.message("progress.text2.replacing", path));
    }
    else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
      myProgress.setText2(SvnBundle.message("progress.text2.transmitting.delta", path));
    }
  }

  public void checkCancelled() {
  }
}
