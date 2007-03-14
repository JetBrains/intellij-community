package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 * @author yole
 */
public class SvnChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangeProvider");

  private SvnVcs myVcs;
  private VcsContextFactory myFactory;
  private SvnBranchConfigurationManager myBranchConfigurationManager;

  public SvnChangeProvider(final SvnVcs vcs) {
    myVcs = vcs;
    myFactory = PeerFactory.getInstance().getVcsContextFactory();
    myBranchConfigurationManager = SvnBranchConfigurationManager.getInstance(myVcs.getProject());
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, ProgressIndicator progress) throws VcsException {
    try {
      final SVNStatusClient client = myVcs.createStatusClient();
      final List<SvnChangedFile> copiedFiles = new ArrayList<SvnChangedFile>();
      final List<SvnChangedFile> deletedFiles = new ArrayList<SvnChangedFile>();
      for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
        processFile(path, client, builder, copiedFiles, deletedFiles, null, true);
      }

      for (FilePath path : dirtyScope.getDirtyFiles()) {
        FileStatus status = getParentStatus(client, path);
        processFile(path, client, builder, copiedFiles, deletedFiles, status, false);
      }

      for(SvnChangedFile copiedFile: copiedFiles) {
        boolean foundRename = false;
        final SVNStatus copiedStatus = copiedFile.getStatus();
        for (Iterator<SvnChangedFile> iterator = deletedFiles.iterator(); iterator.hasNext();) {
          SvnChangedFile deletedFile = iterator.next();
          final SVNStatus deletedStatus = deletedFile.getStatus();
          if (copiedStatus.getCopyFromURL().equals(deletedStatus.getURL().toString())) {
            builder.processChange(new Change(SvnContentRevision.create(deletedFile.getFilePath(), deletedStatus.getRevision()),
                                             CurrentContentRevision.create(copiedFile.getFilePath())));
            iterator.remove();
            foundRename = true;
            break;
          }
        }

        // handle the case when the deleted file wasn't included in the dirty scope - try searching for the local copy
        // by building a relative url
        if (!foundRename) {
          File wcPath = guessWorkingCopyPath(copiedStatus.getFile(), copiedStatus.getURL(), copiedStatus.getCopyFromURL());
          SVNStatus status;
          try {
            status = client.doStatus(wcPath, false);
          }
          catch(SVNException ex) {
            status = null;
          }
          if (status != null && status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
            final FilePath filePath = myFactory.createFilePathOnDeleted(wcPath, false);
            final SvnContentRevision beforeRevision = SvnContentRevision.create(filePath, status.getRevision());
            final ContentRevision afterRevision = CurrentContentRevision.create(copiedFile.getFilePath());
            builder.processChange(new Change(beforeRevision, afterRevision));
            foundRename = true;
          }
        }

        if (!foundRename) {
          processStatus(copiedFile.getFilePath(), copiedStatus, builder, null);
        }
      }
      for(SvnChangedFile deletedFile: deletedFiles) {
        processStatus(deletedFile.getFilePath(), deletedFile.getStatus(), builder, null);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  /**
   * Check if some of the parents of the specified path are switched and/or ignored. These statuses should propagate to
   * the files for which the status was requested even if none of these files are switched or ignored by themselves.
   * (See IDEADEV-13393)
   */
  @Nullable
  private FileStatus getParentStatus(final SVNStatusClient client, final FilePath path) {
    final FilePath parentPath = path.getParentPath();
    if (parentPath == null) {
      return null;
    }
    VirtualFile file = parentPath.getVirtualFile();
    if (file == null) {
      return null;
    }
    FileStatus status = FileStatusManager.getInstance(myVcs.getProject()).getStatus(file);
    if (status == FileStatus.IGNORED) {
      return status;
    }
    FileStatus parentStatus = getParentStatus(client, parentPath);
    if (parentStatus != null) {
      return parentStatus;
    }
    // we care about switched only if none of the parents is ignored
    if (status== FileStatus.SWITCHED) {
      return status;
    }
    return null;
  }

  private static File guessWorkingCopyPath(final File file, final SVNURL url, final String copyFromURL) throws SVNException {
    String copiedPath = url.getPath();
    String copyFromPath = SVNURL.parseURIEncoded(copyFromURL).getPath();
    String commonPathAncestor = SVNPathUtil.getCommonPathAncestor(copiedPath, copyFromPath);
    int pathSegmentCount = SVNPathUtil.getSegmentsCount(copiedPath);
    int ancestorSegmentCount = SVNPathUtil.getSegmentsCount(commonPathAncestor);
    List<String> segments = StringUtil.split(file.getPath(), File.separator);
    List<String> copyFromPathSegments = StringUtil.split(copyFromPath, "/");
    List<String> resultSegments = new ArrayList<String>();
    final int keepSegments = segments.size() - pathSegmentCount + ancestorSegmentCount;
    for(int i=0; i< keepSegments; i++) {
      resultSegments.add(segments.get(i));
    }
    for(int i=ancestorSegmentCount; i<copyFromPathSegments.size(); i++) {
      resultSegments.add(copyFromPathSegments.get(i));
    }
    return new File(StringUtil.join(resultSegments, "/"));
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  private void processFile(FilePath path, SVNStatusClient stClient, final ChangelistBuilder builder,
                           final List<SvnChangedFile> copiedFiles, final List<SvnChangedFile> deletedFiles,
                           final FileStatus parentStatus, final boolean recursively) throws SVNException {
    try {
      if (path.isDirectory()) {
        stClient.doStatus(path.getIOFile(), recursively, false, false, true, new ISVNStatusHandler() {
          public void handleStatus(SVNStatus status) throws SVNException {
            FilePath path = VcsUtil.getFilePath(status.getFile(), status.getKind().equals(SVNNodeKind.DIR));
            processStatusFirstPass(path, status, builder, copiedFiles, deletedFiles, parentStatus);
            if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED && path.isDirectory()) {
              // process children of this file with another client.
              SVNStatusClient client = new SVNStatusClient(null, null);
              if (recursively && path.isDirectory()) {
                VirtualFile[] children = path.getVirtualFile().getChildren();
                for (VirtualFile aChildren : children) {
                  FilePath filePath = VcsUtil.getFilePath(aChildren.getPath(), aChildren.isDirectory());
                  processFile(filePath, client, builder, copiedFiles, deletedFiles, parentStatus, recursively);
                }
              }
            }
          }
        });
      } else {
        processFile(path, stClient, builder, copiedFiles, deletedFiles, parentStatus);
      }
    } catch (SVNException e) {
      if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
        final VirtualFile virtualFile = path.getVirtualFile();
        builder.processUnversionedFile(virtualFile);
        // process children recursively!
        if (recursively && path.isDirectory() && virtualFile != null) {
          VirtualFile[] children = virtualFile.getChildren();
          for (VirtualFile child : children) {
            FilePath filePath = VcsUtil.getFilePath(child.getPath(), child.isDirectory());
            processFile(filePath, stClient, builder, copiedFiles, deletedFiles, parentStatus, recursively);
          }
        }
      }
      else {
        throw e;
      }
    }
  }

  private void processFile(FilePath filePath, SVNStatusClient stClient, ChangelistBuilder builder,
                           final List<SvnChangedFile> copiedFiles, final List<SvnChangedFile> deletedFiles,
                           FileStatus parentStatus) throws SVNException {
    SVNStatus status = stClient.doStatus(filePath.getIOFile(), false, false);
    processStatusFirstPass(filePath, status, builder, copiedFiles, deletedFiles, parentStatus);
  }

  private void processStatusFirstPass(final FilePath filePath, final SVNStatus status, final ChangelistBuilder builder,
                                      final List<SvnChangedFile> copiedFiles, final List<SvnChangedFile> deletedFiles,
                                      final FileStatus parentStatus) throws SVNException {
    if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED && status.getCopyFromURL() != null) {
      copiedFiles.add(new SvnChangedFile(filePath, status));
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      deletedFiles.add(new SvnChangedFile(filePath, status));
    }
    else {
      processStatus(filePath, status, builder, parentStatus);
    }
  }

  private void processStatus(final FilePath filePath, final SVNStatus status, final ChangelistBuilder builder,
                             final FileStatus parentStatus) throws SVNException {
    loadEntriesFile(filePath);
    if (status != null) {
      FileStatus fStatus = convertStatus(status, filePath.getIOFile());

      final SVNStatusType statusType = status.getContentsStatus();
      final SVNStatusType propStatus = status.getPropertiesStatus();
      if (statusType == SVNStatusType.STATUS_UNVERSIONED || statusType == SVNStatusType.UNKNOWN) {
        builder.processUnversionedFile(filePath.getVirtualFile());
      }
      else if (statusType == SVNStatusType.STATUS_CONFLICTED ||
               statusType == SVNStatusType.STATUS_MODIFIED ||
               statusType == SVNStatusType.STATUS_REPLACED ||
               propStatus == SVNStatusType.STATUS_MODIFIED) {
        builder.processChange(new Change(SvnContentRevision.create(filePath, status.getRevision()),
                                         CurrentContentRevision.create(filePath), fStatus));
        checkSwitched(filePath, builder, status, parentStatus);
      }
      else if (statusType == SVNStatusType.STATUS_ADDED) {
        builder.processChange(new Change(null, CurrentContentRevision.create(filePath), fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_DELETED) {
        builder.processChange(new Change(SvnContentRevision.create(filePath, status.getRevision()), null, fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_MISSING) {
        builder.processLocallyDeletedFile(filePath);
      }
      else if (statusType == SVNStatusType.STATUS_IGNORED || parentStatus == FileStatus.IGNORED) {
        builder.processIgnoredFile(filePath.getVirtualFile());
      }
      else if ((fStatus == FileStatus.NOT_CHANGED || fStatus == FileStatus.SWITCHED) && statusType != SVNStatusType.STATUS_NONE) {
        VirtualFile file = filePath.getVirtualFile();
        if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
          builder.processChange(new Change(SvnContentRevision.create(filePath, status.getRevision()),
                                           CurrentContentRevision.create(filePath), FileStatus.MODIFIED));
        }
        checkSwitched(filePath, builder, status, parentStatus);
      }
    }
  }

  private void checkSwitched(final FilePath filePath, final ChangelistBuilder builder, final SVNStatus status, final FileStatus parentStatus) {
    if (status.isSwitched() || parentStatus == FileStatus.SWITCHED) {
      final VirtualFile virtualFile = filePath.getVirtualFile();
      final String switchUrl = status.getURL().toString();
      final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsRootFor(virtualFile);
      String baseUrl = null;
      try {
        baseUrl = myBranchConfigurationManager.get(vcsRoot).getBaseName(switchUrl);
      }
      catch (VcsException e) {
        LOG.error(e);
      }
      builder.processSwitchedFile(virtualFile, baseUrl == null ? switchUrl : baseUrl, true);
    }
  }

  private static FileStatus convertStatus(final SVNStatus status, final File file) throws SVNException {
    if (status == null) {
      return FileStatus.UNKNOWN;
    }
    if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
      return FileStatus.UNKNOWN;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MISSING) {
      return FileStatus.DELETED_FROM_FS;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL) {
      return SvnFileStatus.EXTERNAL;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
      return SvnFileStatus.OBSTRUCTED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED) {
      return FileStatus.IGNORED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
      return FileStatus.ADDED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      return FileStatus.DELETED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_REPLACED) {
      return SvnFileStatus.REPLACED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED ||
             status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
      return FileStatus.MERGED_WITH_CONFLICTS;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED ||
             status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
      return FileStatus.MODIFIED;
    }
    else if (status.isSwitched()) {
      return FileStatus.SWITCHED;
    }
    else if (status.isCopied()) {
      return FileStatus.ADDED;
    }
    if (file.isDirectory() && file.getParentFile() != null) {
      String childURL = null;
      String parentURL = null;
      SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
      try {
        wcAccess.open(file, false, 0);
        SVNAdminArea parentDir = wcAccess.open(file.getParentFile(), false, 0);
        if (wcAccess.getEntry(file, false) != null) {
          childURL = wcAccess.getEntry(file, false).getURL();
        }
        if (parentDir != null) {
          final SVNEntry parentDirEntry = parentDir.getEntry("", false);
          if (parentDirEntry != null) {
            parentURL = parentDirEntry.getURL();
          }
        }
      }
      finally {
        try {
          wcAccess.close();
        } catch (SVNException e) {
          //
        }
      }
        try {
            if (parentURL != null && !SVNWCUtil.isWorkingCopyRoot(file)) {
              parentURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(file.getName()));
              if (childURL != null && !parentURL.equals(childURL)) {
                return FileStatus.SWITCHED;
              }
            }
        } catch (SVNException e) {
            //
        }
    }
    return FileStatus.NOT_CHANGED;
  }

  public static void loadEntriesFile(final FilePath filePath) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (filePath.getParentPath() == null) {
              return;
            }
            File svnSubdirectory = new File(filePath.getParentPath().getIOFile(), SvnUtil.SVN_ADMIN_DIR_NAME);
            LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
            VirtualFile file = localFileSystem.refreshAndFindFileByIoFile(svnSubdirectory);
            if (file != null) {
              localFileSystem.refreshAndFindFileByIoFile(new File(svnSubdirectory, SvnUtil.ENTRIES_FILE_NAME));
            }
            if (filePath.isDirectory()) {
              svnSubdirectory = new File(filePath.getPath(), SvnUtil.SVN_ADMIN_DIR_NAME);
              file = localFileSystem.refreshAndFindFileByIoFile(svnSubdirectory);
              if (file != null) {
                localFileSystem.refreshAndFindFileByIoFile(new File(svnSubdirectory, SvnUtil.ENTRIES_FILE_NAME));
              }
            }
          }
        });
      }
    });
  }

  private static class SvnChangedFile {
    private FilePath myFilePath;
    private SVNStatus myStatus;

    public SvnChangedFile(final FilePath filePath, final SVNStatus status) {
      myFilePath = filePath;
      myStatus = status;
    }

    public FilePath getFilePath() {
      return myFilePath;
    }

    public SVNStatus getStatus() {
      return myStatus;
    }
  }
}
