package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusClient;

public class StatusWalkerPartnerImpl implements StatusWalkerPartner {
  private final SvnVcs myVcs;
  private final ChangeListManager myClManager;
  private final ExcludedFileIndex myExcludedFileIndex;
  private final ProgressIndicator myIndicator;
  private ISVNStatusFileProvider myFileProvider;

  public StatusWalkerPartnerImpl(final SvnVcs vcs, final ProgressIndicator pi) {
    myVcs = vcs;
    myClManager = ChangeListManager.getInstance(myVcs.getProject());
    myExcludedFileIndex = ExcludedFileIndex.getInstance(myVcs.getProject());
    myIndicator = pi;
  }

  public void setFileProvider(final ISVNStatusFileProvider fileProvider) {
    myFileProvider = fileProvider;
  }

  public SVNStatusClient createStatusClient() {
    final SVNStatusClient result = myVcs.createStatusClient();
    result.setFilesProvider(myFileProvider);
    result.setEventHandler(new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) throws SVNException {
        //
      }

      public void checkCancelled() throws SVNCancelException {
        if (myIndicator != null) {
          myIndicator.checkCanceled();
        }
      }
    });
    return result;
  }

  public void checkCanceled() {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
    }
  }

  public boolean isExcluded(VirtualFile vFile) {
    return myExcludedFileIndex.isExcludedFile(vFile);
  }

  public boolean isIgnoredIdeaLevel(VirtualFile vFile) {
    return myClManager.isIgnoredFile(vFile);
  }
}
