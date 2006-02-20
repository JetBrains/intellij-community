package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;

/**
 * @author max
 */
public class SvnChangeProvider implements ChangeProvider {
  private SvnVcs myVcs;

  public SvnChangeProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress) {
    dirtyScope.iterate(new ContentIterator() {
      public boolean processFile(final VirtualFile vFile) {
        SVNStatus status;
        final File[] ioFile = new File[] {null};
        try {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (vFile == null) {
                return;
              }
              ioFile[0] = new File(vFile.getPath());
            }
          });

          SVNStatusClient stClient = myVcs.createStatusClient();
          status = stClient.doStatus(ioFile[0], false, true);

          if (status != null) {
            final SVNStatusType statusType = status.getContentsStatus();
            if (statusType == SVNStatusType.STATUS_UNVERSIONED) {
              builder.processUnversionedFile(vFile);
            }
            else if (statusType == SVNStatusType.STATUS_CONFLICTED ||
                     statusType == SVNStatusType.STATUS_MERGED ||
                     statusType == SVNStatusType.STATUS_MODIFIED) {
              final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(vFile);
              builder.processChange(new Change(new SvnUpToDateRevision(filePath, myVcs), new CurrentContentRevision(filePath)));
            }
            else if (statusType == SVNStatusType.STATUS_ADDED) {
              final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(vFile);
              builder.processChange(new Change(null, new CurrentContentRevision(filePath)));
            }
            else if (statusType == SVNStatusType.STATUS_DELETED) {
              final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(vFile);
              builder.processChange(new Change(new SvnUpToDateRevision(filePath, myVcs), null));
            }
          }
        }
        catch (SVNException e) {
          // Ignore
        }

        return true;
      }
    });
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
