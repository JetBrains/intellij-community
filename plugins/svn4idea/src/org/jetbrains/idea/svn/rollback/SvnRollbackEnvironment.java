package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.EmptyChangelistBuilder;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnChangeProvider;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SvnRollbackEnvironment extends DefaultRollbackEnvironment {
  private final SvnVcs mySvnVcs;

  public SvnRollbackEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  public List<VcsException> rollbackChanges(List<Change> changes) {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    for (Change change : changes) {
      File beforePath = null;
      ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        beforePath = beforeRevision.getFile().getIOFile();
        checkRevertFile(beforePath, exceptions);
      }
      ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        File afterPath = afterRevision.getFile().getIOFile();
        if (!afterPath.equals(beforePath)) {
          UnversionedFilesCollector collector = new UnversionedFilesCollector();
          try {
            ((SvnChangeProvider) mySvnVcs.getChangeProvider()).getChanges(afterRevision.getFile(), false, collector);
          }
          catch (SVNException e) {
            exceptions.add(new VcsException(e));
          }
          checkRevertFile(afterPath, exceptions);
          // rolling back a rename should delete the after file
          if (beforePath != null) {
            for(VirtualFile f: collector.getUnversionedFiles()) {
              File ioFile = new File(f.getPath());
              ioFile.renameTo(new File(beforePath, ioFile.getName()));
            }
            FileUtil.delete(afterPath);
          }
        }
      }
    }

    return exceptions;
  }

  private void checkRevertFile(final File ioFile, final List<VcsException> exceptions) {
    try {
      SVNWCClient client = mySvnVcs.createWCClient();
      client.setEventHandler(new ISVNEventHandler() {
        public void handleEvent(SVNEvent event, double progress) {
          if (event.getAction() == SVNEventAction.FAILED_REVERT) {
            exceptions.add(new VcsException("Revert failed"));
          }
        }

        public void checkCancelled() {
        }
      });
      client.doRevert(ioFile, false);
    }
    catch (SVNException e) {
      if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
        // skip errors on unversioned resources.
        exceptions.add(new VcsException(e));
      }
    }
  }

  public List<VcsException> rollbackMissingFileDeletion(List<FilePath> filePaths) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    final SVNWCClient wcClient = mySvnVcs.createWCClient();

    List<File> files = ChangesUtil.filePathsToFiles(filePaths);
    for (File file : files) {
      try {
        SVNInfo info = wcClient.doInfo(file, SVNRevision.BASE);
        if (info != null && info.getKind() == SVNNodeKind.FILE) {
          wcClient.doRevert(file, false);
        } else {
          // do update to restore missing directory.
          mySvnVcs.createUpdateClient().doUpdate(file, SVNRevision.HEAD, true);
        }
      }
      catch (SVNException e) {
        exceptions.add(new VcsException(e));
      }
    }

    return exceptions;
  }

  private static class UnversionedFilesCollector extends EmptyChangelistBuilder {
    private List<VirtualFile> myUnversionedFiles = new ArrayList<VirtualFile>();

    public void processUnversionedFile(final VirtualFile file) {
      myUnversionedFiles.add(file);
    }

    public List<VirtualFile> getUnversionedFiles() {
      return myUnversionedFiles;
    }
  }
}