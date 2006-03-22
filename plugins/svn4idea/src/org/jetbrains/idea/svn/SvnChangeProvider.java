package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.util.List;

/**
 * @author max
 */
public class SvnChangeProvider implements ChangeProvider {
  private SvnVcs myVcs;

  public SvnChangeProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress) {
    try {
      final SVNStatusClient client = myVcs.createStatusClient();
      for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
        processFile(path, client, builder, true);
      }

      for (FilePath path : dirtyScope.getDirtyFiles()) {
        processFile(path, client, builder, false);
      }
    }
    catch (SVNException e) {
      // Ignore
    }
  }

  public List<VcsException> rollbackChanges(List<Change> changes) {
    //TODO
    throw new UnsupportedOperationException();    
  }

  private void processFile(FilePath path, SVNStatusClient stClient, final ChangelistBuilder builder, boolean recursively)
    throws SVNException {
    if (path.isDirectory()) {
      stClient.doStatus(path.getIOFile(), recursively, false, false, false, new ISVNStatusHandler() {
        public void handleStatus(SVNStatus status) throws SVNException {
          if (status.getKind() == SVNNodeKind.FILE) {
            processStatus(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(status.getFile()), status, builder);
          }
        }
      });
    }
    else {
      processFile(path, stClient, builder);
    }
  }

  private void processFile(FilePath filePath, SVNStatusClient stClient, ChangelistBuilder builder) throws SVNException {
    SVNStatus status = stClient.doStatus(filePath.getIOFile(), false, true);
    processStatus(filePath, status, builder);
  }

  private void processStatus(final FilePath filePath, final SVNStatus status, final ChangelistBuilder builder) {
    if (status != null) {
      final SVNStatusType statusType = status.getContentsStatus();
      if (statusType == SVNStatusType.STATUS_UNVERSIONED) {
        builder.processUnversionedFile(filePath.getVirtualFile());
      }
      else if (statusType == SVNStatusType.STATUS_CONFLICTED ||
               statusType == SVNStatusType.STATUS_MERGED ||
               statusType == SVNStatusType.STATUS_MODIFIED) {
        builder.processChange(new Change(new SvnUpToDateRevision(filePath, myVcs), new CurrentContentRevision(filePath)));
      }
      else if (statusType == SVNStatusType.STATUS_ADDED) {
        builder.processChange(new Change(null, new CurrentContentRevision(filePath)));
      }
      else if (statusType == SVNStatusType.STATUS_DELETED) {
        builder.processChange(new Change(new SvnUpToDateRevision(filePath, myVcs), null));
      }
    }
  }

  private static class SvnUpToDateRevision implements ContentRevision {
    private FilePath myFile;
    private String myContent = null;
    private SvnVcs myVcs;

    public SvnUpToDateRevision(final FilePath file, final SvnVcs vcs) {
      myVcs = vcs;
      myFile = file;
    }

    @Nullable
    public String getContent() {
      if (myContent == null) {
        try {
          myContent = myVcs.getUpToDateRevisionProvider().getLastUpToDateContentFor(myFile.getVirtualFile(), true);
        }
        catch (VcsException e) {
          // Ignore
        }
      }
      return myContent;
    }

    @NotNull
    public FilePath getFile() {
      return myFile;
    }
  }
}
