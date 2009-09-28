package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;

/**
 * @author yole
 */
public class SvnProgressCanceller implements ISVNEventHandler {
  public void checkCancelled() throws SVNCancelException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null && indicator.isCanceled()) {
      throw new SVNCancelException();
    }
  }

  public void handleEvent(final SVNEvent event, final double progress) throws SVNException {
  }
}