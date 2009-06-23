package org.jetbrains.idea.svn;

import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;

public class StatusWalkerPartnerImpl implements StatusWalkerPartner {
  private final SvnVcs myVcs;
  private final ChangeListManager myClManager;
  private final ExcludedFileIndex myExcludedFileIndex;
  private final ProgressIndicator myIndicator;
  private ISVNStatusFileProvider myFileProvider;

  public StatusWalkerPartnerImpl(final SvnVcs vcs) {
    myVcs = vcs;
    myClManager = ChangeListManager.getInstance(myVcs.getProject());
    myExcludedFileIndex = ExcludedFileIndex.getInstance(myVcs.getProject());
    myIndicator = ProgressManager.getInstance().getProgressIndicator();
  }

  public void setFileProvider(final ISVNStatusFileProvider fileProvider) {
    myFileProvider = fileProvider;
  }

  public SVNStatusClient createStatusClient() {
    final SVNStatusClient result = myVcs.createStatusClient();
    result.setFilesProvider(myFileProvider);
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
