package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class SvnChangeProvider implements ChangeProvider {
  private SvnVcs myVcs;
  private VcsContextFactory myFactory;

  public SvnChangeProvider(final SvnVcs vcs) {
    myVcs = vcs;
    myFactory = PeerFactory.getInstance().getVcsContextFactory();
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, ProgressIndicator progress) throws VcsException {
    try {
      final SVNStatusClient client = myVcs.createStatusClient();
      final List<SvnChangedFile> copiedFiles = new ArrayList<SvnChangedFile>();
      final List<SvnChangedFile> deletedFiles = new ArrayList<SvnChangedFile>();
      for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
        processFile(path, client, builder, copiedFiles, deletedFiles, true);
      }

      for (FilePath path : dirtyScope.getDirtyFiles()) {
        processFile(path, client, builder, copiedFiles, deletedFiles, false);
      }

      for(SvnChangedFile copiedFile: copiedFiles) {
        boolean foundRename = false;
        final SVNStatus copiedStatus = copiedFile.getStatus();
        for (Iterator<SvnChangedFile> iterator = deletedFiles.iterator(); iterator.hasNext();) {
          SvnChangedFile deletedFile = iterator.next();
          final SVNStatus deletedStatus = deletedFile.getStatus();
          if (copiedStatus.getCopyFromURL().equals(deletedStatus.getURL().toString())) {
            builder.processChange(new Change(SvnUpToDateRevision.create(deletedFile.getFilePath(), deletedStatus.getRevision()),
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
            final SvnUpToDateRevision beforeRevision = SvnUpToDateRevision.create(filePath, status.getRevision());
            final ContentRevision afterRevision = CurrentContentRevision.create(copiedFile.getFilePath());
            builder.processChange(new Change(beforeRevision, afterRevision));
            foundRename = true;
          }
        }

        if (!foundRename) {
          processStatus(copiedFile.getFilePath(), copiedStatus, builder);
        }
      }
      for(SvnChangedFile deletedFile: deletedFiles) {
        processStatus(deletedFile.getFilePath(), deletedFile.getStatus(), builder);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
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

  private static void processFile(FilePath path, SVNStatusClient stClient, final ChangelistBuilder builder,
                                  final List<SvnChangedFile> copiedFiles, final List<SvnChangedFile> deletedFiles, final boolean recursively) throws SVNException {
    try {
      if (path.isDirectory()) {
        stClient.doStatus(path.getIOFile(), recursively, false, false, true, new ISVNStatusHandler() {
          public void handleStatus(SVNStatus status) throws SVNException {
            FilePath path = VcsUtil.getFilePath(status.getFile(), status.getKind().equals(SVNNodeKind.DIR));
            processStatusFirstPass(path, status, builder, copiedFiles, deletedFiles);
            if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED && path.isDirectory()) {
              // process children of this file with another client.
              SVNStatusClient client = new SVNStatusClient(null, null);
              if (recursively && path.isDirectory()) {
                VirtualFile[] children = path.getVirtualFile().getChildren();
                for (VirtualFile aChildren : children) {
                  FilePath filePath = VcsUtil.getFilePath(aChildren.getPath(), aChildren.isDirectory());
                  processFile(filePath, client, builder, copiedFiles, deletedFiles, recursively);
                }
              }
            }
          }
        });
      } else {
        processFile(path, stClient, builder, copiedFiles, deletedFiles);
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
            processFile(filePath, stClient, builder, copiedFiles, deletedFiles, recursively);
          }
        }
      }
      else {
        throw e;
      }
    }
  }

  private static void processFile(FilePath filePath, SVNStatusClient stClient, ChangelistBuilder builder,
                                  final List<SvnChangedFile> copiedFiles, final List<SvnChangedFile> deletedFiles) throws SVNException {
    SVNStatus status = stClient.doStatus(filePath.getIOFile(), false, false);
    processStatusFirstPass(filePath, status, builder, copiedFiles, deletedFiles);
  }

  private static void processStatusFirstPass(final FilePath filePath, final SVNStatus status, final ChangelistBuilder builder,
                                             final List<SvnChangedFile> copiedFiles, final List<SvnChangedFile> deletedFiles) throws SVNException {
    if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED && status.getCopyFromURL() != null) {
      copiedFiles.add(new SvnChangedFile(filePath, status));
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      deletedFiles.add(new SvnChangedFile(filePath, status));
    }
    else {
      processStatus(filePath, status, builder);
    }
  }

  private static void processStatus(final FilePath filePath, final SVNStatus status, final ChangelistBuilder builder) throws SVNException {
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
        builder.processChange(new Change(SvnUpToDateRevision.create(filePath, status.getRevision()),
                                         CurrentContentRevision.create(filePath), fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_ADDED) {
        builder.processChange(new Change(null, CurrentContentRevision.create(filePath), fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_DELETED) {
        builder.processChange(new Change(SvnUpToDateRevision.create(filePath, status.getRevision()), null, fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_MISSING) {
        builder.processLocallyDeletedFile(filePath);
      }
      else if (statusType == SVNStatusType.STATUS_IGNORED) {
        builder.processIgnoredFile(filePath.getVirtualFile());
      }
      else if (fStatus == FileStatus.NOT_CHANGED && statusType != SVNStatusType.STATUS_NONE) {
        VirtualFile file = filePath.getVirtualFile();
        if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
          builder.processChange(new Change(SvnUpToDateRevision.create(filePath, status.getRevision()),
                                           CurrentContentRevision.create(filePath), FileStatus.MODIFIED));
        }
      }
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
      return SvnFileStatus.SWITCHED;
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
          parentURL = parentDir.getEntry("", false).getURL();
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
                return SvnFileStatus.SWITCHED;
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

  private static class SvnUpToDateRevision implements ContentRevision {
    private final FilePath myFile;
    private String myContent = null;
    private VcsRevisionNumber myRevNumber;

    protected SvnUpToDateRevision(@NotNull final FilePath file, final SVNRevision revision) {
      myFile = file;
      myRevNumber = new SvnRevisionNumber(revision);
    }

    public static SvnUpToDateRevision create(@NotNull final FilePath file, final SVNRevision revision) {
      if (file.getFileType().isBinary()) {
        return new SvnUpToDateBinaryRevision(file, revision);
      }
      return new SvnUpToDateRevision(file, revision);
    }

    @Nullable
    public String getContent() throws VcsException {
      if (myContent == null) {
        try {
          myContent = new String(getUpToDateBinaryContent(), myFile.getCharset().name());
        }
        catch(Exception ex) {
          throw new VcsException(ex);
        }
      }
      return myContent;
    }

    @Nullable
    protected byte[] getUpToDateBinaryContent() throws SVNException, IOException {
      File file = myFile.getIOFile();
      File lock = new File(file.getParentFile(), SvnUtil.PATH_TO_LOCK_FILE);
      if (lock.exists()) {
        return null;
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      SVNWCClient wcClient = new SVNWCClient(null, null);
      wcClient.doGetFileContents(file, SVNRevision.UNDEFINED, SVNRevision.BASE, true, buffer);
      buffer.close();
      return buffer.toByteArray();
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

  private static class SvnUpToDateBinaryRevision extends SvnUpToDateRevision implements BinaryContentRevision {
    public SvnUpToDateBinaryRevision(@NotNull final FilePath file, final SVNRevision revision) {
      super(file, revision);
    }

    @Nullable
    public byte[] getBinaryContent() throws VcsException {
      try {
        return getUpToDateBinaryContent();
      }
      catch(Exception ex) {
        throw new VcsException(ex);
      }
    }
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
