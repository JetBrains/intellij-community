package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
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

  // TODO: Get rid of CheckitEnvironment and move real commit code here.
  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    final List<FilePath> paths = ChangesUtil.getPaths(changes);
    FilePath[] arrayed = paths.toArray(new FilePath[paths.size()]);
    return myVcs.getCheckinEnvironment().commit(arrayed, myVcs.getProject(), preparedComment);
  }

  public List<VcsException> rollbackChanges(List<Change> changes) {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    for (Change change : changes) {
      final File ioFile = ChangesUtil.getFilePath(change).getIOFile();
      try {
        SVNWCClient client = myVcs.createWCClient();
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
        exceptions.add(new VcsException(e));
      }
    }

    return exceptions;
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<File> files) {
    return processMissingFiles(files, true);
  }

  public List<VcsException> rollbackMissingFileDeletion(List<File> files) {
    return processMissingFiles(files, false);
  }

  private List<VcsException> processMissingFiles(final List<File> files, final boolean delete) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    final SVNWCClient wcClient;
    try {
      wcClient = myVcs.createWCClient();
    }
    catch (SVNException e) {
      exceptions.add(new VcsException(e));
      return exceptions;
    }

    for (File file : files) {
      try {
        if (delete) {
          wcClient.doDelete(file, true, false);
        }
        else {
          wcClient.doRevert(file, false);
        }
      }
      catch (SVNException e) {
        exceptions.add(new VcsException(e));
      }
    }

    return exceptions;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    final SVNWCClient wcClient;
    try {
      wcClient = myVcs.createWCClient();
    }
    catch (SVNException e) {
      exceptions.add(new VcsException(e));
      return exceptions;
    }

    for (VirtualFile file : files) {
      try {
        wcClient.doAdd(new File(FileUtil.toSystemDependentName(file.getPath())), true, true, true, false);
      }
      catch (SVNException e) {
        exceptions.add(new VcsException(e));
      }
    }

    return exceptions;
  }

  private void processFile(FilePath path, SVNStatusClient stClient, final ChangelistBuilder builder, boolean recursively)
    throws SVNException {
    if (path.isDirectory()) {
      stClient.doStatus(path.getIOFile(), recursively, false, false, false, new ISVNStatusHandler() {
        public void handleStatus(SVNStatus status) throws SVNException {
          processStatus(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(status.getFile()), status, builder);
        }
      });
    } else {
      processFile(path, stClient, builder);
    }
  }

  private void processFile(FilePath filePath, SVNStatusClient stClient, ChangelistBuilder builder) throws SVNException {
    SVNStatus status = stClient.doStatus(filePath.getIOFile(), false, false);
    processStatus(filePath, status, builder);
  }

  private void processStatus(final FilePath filePath, final SVNStatus status, final ChangelistBuilder builder) {
    if (status != null) {
      FileStatus fStatus = SvnFileStatusProvider.convertStatus(status, filePath.getIOFile());

      final SVNStatusType statusType = status.getContentsStatus();
      final SVNStatusType propStatus = status.getPropertiesStatus();
      if (statusType == SVNStatusType.STATUS_UNVERSIONED || statusType == SVNStatusType.UNKNOWN) {
        builder.processUnversionedFile(filePath.getVirtualFile());
      }
      else if (statusType == SVNStatusType.STATUS_CONFLICTED ||
               statusType == SVNStatusType.STATUS_MODIFIED ||
               statusType == SVNStatusType.STATUS_REPLACED ||
               propStatus == SVNStatusType.STATUS_MODIFIED) {
        builder.processChange(new Change(new SvnUpToDateRevision(filePath, status.getRevision()), new CurrentContentRevision(filePath), fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_ADDED) {
        builder.processChange(new Change(null, new CurrentContentRevision(filePath), fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_DELETED) {
        builder.processChange(new Change(new SvnUpToDateRevision(filePath, status.getRevision()), null, fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_MISSING) {
        builder.processLocallyDeletedFile(filePath.getIOFile());
      }
    }
  }

  private static class SvnUpToDateRevision implements ContentRevision {
    private final FilePath myFile;
    private String myContent = null;
    private VcsRevisionNumber myRevNumber;

    public SvnUpToDateRevision(@NotNull final FilePath file, final SVNRevision revision) {
      myFile = file;
      myRevNumber = new SvnRevisionNumber(revision);
    }

    @Nullable
    public String getContent() {
      if (myContent == null) {
        myContent = SvnUpToDateRevisionProvider.getLastUpToDateContentFor(myFile.getIOFile(), myFile.getCharset().name());
      }
      return myContent;
    }

    @NotNull
    public FilePath getFile() {
      return myFile;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return myRevNumber;
    }
  }
}
