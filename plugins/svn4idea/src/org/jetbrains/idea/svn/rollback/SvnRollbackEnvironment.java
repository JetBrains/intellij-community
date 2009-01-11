package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.EmptyChangelistBuilder;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnChangeProvider;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class SvnRollbackEnvironment extends DefaultRollbackEnvironment {
  private final SvnVcs mySvnVcs;

  public SvnRollbackEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  @Override
  public String getRollbackOperationName() {
    return SvnBundle.message("action.name.revert");
  }

  public void rollbackChanges(List<Change> changes, final List<VcsException> exceptions, @NotNull final RollbackProgressListener listener) {
    listener.indeterminate();
    final SvnChangeProvider changeProvider = (SvnChangeProvider) mySvnVcs.getChangeProvider();
    
    final UnversionedFilesGroupCollector collector = new UnversionedFilesGroupCollector();
    final Set<String> files = new HashSet<String>();
    for (Change change : changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        files.add(beforeRevision.getFile().getIOFile().getAbsolutePath());
      }
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final String afterPath = afterRevision.getFile().getIOFile().getAbsolutePath();
        files.add(afterPath);

        if ((beforeRevision != null) && (! afterPath.equals(beforeRevision.getFile().getIOFile().getAbsolutePath()))) {
          // move/rename
          collector.setBefore(beforeRevision.getFile().getIOFile(), afterRevision.getFile().getIOFile());
          try {
            changeProvider.getChanges(afterRevision.getFile(), false, collector);
          }
          catch (SVNException e) {
            exceptions.add(new VcsException(e));
          }
        }
      }
    }

    final File[] filesArr = new File[files.size()];
    int i = 0;
    for (String file : files) {
      filesArr[i] = new File(file);
      ++ i;
    }

    try {
      final SVNWCClient client = mySvnVcs.createWCClient();
      client.setEventHandler(new ISVNEventHandler() {
        public void handleEvent(SVNEvent event, double progress) {
          if (event.getAction() == SVNEventAction.REVERT) {
            final File file = event.getFile();
            if (file != null) {
              listener.accept(file);
            }
          }
          if (event.getAction() == SVNEventAction.FAILED_REVERT) {
            exceptions.add(new VcsException("Revert failed"));
          }
        }

        public void checkCancelled() {
          listener.checkCanceled();
        }
      });
      client.doRevert(filesArr, SVNDepth.EMPTY, null);
    }
    catch (SVNException e) {
      if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
        // skip errors on unversioned resources.
        exceptions.add(new VcsException(e));
      }
    }

    final List<Trinity<File, File, File>> fromTo = collector.getFromTo();
    for (Trinity<File, File, File> trinity : fromTo) {
      if (trinity.getFirst().exists()) {
        // parent successfully renamed/moved
        trinity.getSecond().renameTo(trinity.getThird());
      }
    }
    final List<Pair<File, File>> toBeDeleted = collector.getToBeDeleted();
    for (Pair<File, File> pair : toBeDeleted) {
      if (pair.getFirst().exists()) {
        FileUtil.delete(pair.getSecond());
      }
    }
  }

  public void rollbackMissingFileDeletion(List<FilePath> filePaths, final List<VcsException> exceptions,
                                                        final RollbackProgressListener listener) {
    final SVNWCClient wcClient = mySvnVcs.createWCClient();

    List<File> files = ChangesUtil.filePathsToFiles(filePaths);
    for (File file : files) {
      listener.accept(file);
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
  }

  private static class UnversionedFilesGroupCollector extends EmptyChangelistBuilder {
    private File myCurrentBeforeFile;
    private List<Pair<File, File>> myToBeDeleted;
    private List<Trinity<File, File, File>> myFromTo;

    private UnversionedFilesGroupCollector() {
      myFromTo = new ArrayList<Trinity<File, File, File>>();
      myToBeDeleted = new ArrayList<Pair<File, File>>();
    }

    @Override
    public void processUnversionedFile(final VirtualFile file) {
      final File to = new File(myCurrentBeforeFile, file.getName());
      myFromTo.add(new Trinity<File, File, File>(myCurrentBeforeFile, new File(file.getPath()), to));
    }

    public void setBefore(@NotNull final File beforeFile, @NotNull final File afterFile) {
      myCurrentBeforeFile = beforeFile;
      myToBeDeleted.add(new Pair<File, File>(beforeFile, afterFile));
    }

    public List<Pair<File, File>> getToBeDeleted() {
      return myToBeDeleted;
    }

    public List<Trinity<File, File, File>> getFromTo() {
      return myFromTo;
    }
  }
}
