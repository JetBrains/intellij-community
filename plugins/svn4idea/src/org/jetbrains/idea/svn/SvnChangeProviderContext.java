/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.util.*;

class SvnChangeProviderContext implements StatusReceiver {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnChangeProviderContext");

  private final ChangelistBuilder myChangelistBuilder;
  private final SVNStatusClient myStatusClient;
  private List<SvnChangedFile> myCopiedFiles = null;
  private final List<SvnChangedFile> myDeletedFiles = new ArrayList<SvnChangedFile>();
  // for files moved in a subtree, which were the targets of merge (for instance).
  private final Map<String, SVNStatus> myTreeConflicted;
  private Map<FilePath, String> myCopyFromURLs = null;
  private final SvnVcs myVcs;
  private final SvnBranchConfigurationManager myBranchConfigurationManager;

  private final ProgressIndicator myProgress;

  public SvnChangeProviderContext(SvnVcs vcs, final ChangelistBuilder changelistBuilder, final ProgressIndicator progress) {
    myVcs = vcs;
    myStatusClient = vcs.createStatusClient();
    myChangelistBuilder = changelistBuilder;
    myProgress = progress;
    myTreeConflicted = new HashMap<String, SVNStatus>();
    myBranchConfigurationManager = SvnBranchConfigurationManager.getInstance(myVcs.getProject());
  }

  public void process(FilePath path, SVNStatus status, boolean isInnerCopyRoot) throws SVNException {
    processStatusFirstPass(path, status);
  }

  public void processIgnored(VirtualFile vFile) {
    myChangelistBuilder.processIgnoredFile(vFile);
  }

  public void processUnversioned(VirtualFile vFile) {
    myChangelistBuilder.processUnversionedFile(vFile);
  }

  public ChangelistBuilder getBuilder() {
    return myChangelistBuilder;
  }

  public SVNStatusClient getClient() {
    return myStatusClient;
  }

  public void reportTreeConflict(final SVNStatus status) {
    myTreeConflicted.put(status.getFile().getAbsolutePath(), status);
  }

  @Nullable
  public SVNStatus getTreeConflictStatus(final File file) {
    return myTreeConflicted.get(file.getAbsolutePath());
  }

  @NotNull
  public List<SvnChangedFile> getCopiedFiles() {
    if (myCopiedFiles == null) {
      return Collections.emptyList();
    }
    return myCopiedFiles;
  }

  public List<SvnChangedFile> getDeletedFiles() {
    return myDeletedFiles;
  }

  public boolean isDeleted(final FilePath path) {
    for (SvnChangedFile deletedFile : myDeletedFiles) {
      if (Comparing.equal(path, deletedFile.getFilePath())) {
        return true;
      }
    }
    return false;
  }

  public boolean isCanceled() {
    return (myProgress != null) && myProgress.isCanceled();
  }

  /**
   * If the specified filepath or its parent was added with history, returns the URL of the copy source for this filepath.
   *
   * @param filePath the original filepath
   * @return the copy source url, or null if the file isn't a copy of anything
   */
  @Nullable
  public String getParentCopyFromURL(FilePath filePath) {
    if (myCopyFromURLs == null) {
      return null;
    }
    StringBuilder relPathBuilder = new StringBuilder();
    while(filePath != null) {
      String copyFromURL = myCopyFromURLs.get(filePath);
      if (copyFromURL != null) {
        return copyFromURL + relPathBuilder.toString();
      }
      relPathBuilder.insert(0, "/" + filePath.getName());
      filePath = filePath.getParentPath();
    }
    return null;
  }

  public void addCopiedFile(final FilePath filePath, final SVNStatus status, final String copyFromURL) {
    if (myCopiedFiles == null) {
      myCopiedFiles = new ArrayList<SvnChangedFile>();
    }
    myCopiedFiles.add(new SvnChangedFile(filePath, status, copyFromURL));
    final String url = status.getCopyFromURL();
    if (url != null) {
      addCopyFromURL(filePath, url);
    }
  }

  public void addCopyFromURL(final FilePath filePath, final String url) {
    if (myCopyFromURLs == null) {
      myCopyFromURLs = new HashMap<FilePath, String>();
    }
    myCopyFromURLs.put(filePath, url);
  }

//

  void processStatusFirstPass(final FilePath filePath, final SVNStatus status) throws SVNException {
    if (status == null) {
      // external to wc
      return;
    }
    if (status.getRemoteLock() != null) {
      final SVNLock lock = status.getRemoteLock();
      myChangelistBuilder.processLogicallyLockedFolder(filePath.getVirtualFile(),
                                                          new LogicalLock(false, lock.getOwner(), lock.getComment(), lock.getCreationDate(), lock.getExpirationDate()));
    }
    if (status.getLocalLock() != null) {
      final SVNLock lock = status.getLocalLock();
      myChangelistBuilder.processLogicallyLockedFolder(filePath.getVirtualFile(),
                                                          new LogicalLock(true, lock.getOwner(), lock.getComment(), lock.getCreationDate(), lock.getExpirationDate()));
    }
    if (filePath.isDirectory() && status.isLocked()) {
      myChangelistBuilder.processLockedFolder(filePath.getVirtualFile());
    }
    if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED && status.getCopyFromURL() != null) {
      addCopiedFile(filePath, status, status.getCopyFromURL());
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      myDeletedFiles.add(new SvnChangedFile(filePath, status));
    }
    else {
      String parentCopyFromURL = getParentCopyFromURL(filePath);
      if (parentCopyFromURL != null) {
        addCopiedFile(filePath, status, parentCopyFromURL);
      } else {
        processStatus(filePath, status);
      }
    }
  }

  void processStatus(final FilePath filePath, final SVNStatus status) throws SVNException {
    loadEntriesFile(filePath);
    if (status != null) {
      FileStatus fStatus = SvnStatusConvertor.convertStatus(status);

      final SVNStatusType statusType = status.getContentsStatus();
      final SVNStatusType propStatus = status.getPropertiesStatus();
      if (statusType == SVNStatusType.STATUS_UNVERSIONED || statusType == SVNStatusType.UNKNOWN) {
        final VirtualFile file = filePath.getVirtualFile();
        if (file != null) {
          myChangelistBuilder.processUnversionedFile(file);
        }
      }
      else if (statusType == SVNStatusType.STATUS_CONFLICTED ||
               statusType == SVNStatusType.STATUS_MODIFIED ||
               statusType == SVNStatusType.STATUS_REPLACED ||
               propStatus == SVNStatusType.STATUS_MODIFIED ||
               propStatus == SVNStatusType.STATUS_CONFLICTED) {
        myChangelistBuilder.processChangeInList(createChange(SvnContentRevision.createBaseRevision(myVcs, filePath,
                                                                                                   status.getCommittedRevision()),
                                                 CurrentContentRevision.create(filePath), fStatus, status), changeListNameFromStatus(status),
                                                SvnVcs.getKey());
        checkSwitched(filePath, myChangelistBuilder, status, fStatus);
      }
      else if (statusType == SVNStatusType.STATUS_ADDED) {
        myChangelistBuilder.processChangeInList(createChange(null, CurrentContentRevision.create(filePath), fStatus, status), changeListNameFromStatus(status),
                                                SvnVcs.getKey());
      }
      else if (statusType == SVNStatusType.STATUS_DELETED) {
        myChangelistBuilder.processChangeInList(createChange(SvnContentRevision.createBaseRevision(myVcs, filePath,
                                                                                                   status.getCommittedRevision()), null, fStatus, status),
                                    changeListNameFromStatus(status), SvnVcs.getKey());
      }
      else if (statusType == SVNStatusType.STATUS_MISSING) {
        myChangelistBuilder.processLocallyDeletedFile(createLocallyDeletedChange(filePath, status));
      }
      else if (statusType == SVNStatusType.STATUS_IGNORED) {
        myChangelistBuilder.processIgnoredFile(filePath.getVirtualFile());
      }
      else if (status.isCopied()) {
        //
      }
      else if ((fStatus == FileStatus.NOT_CHANGED || fStatus == FileStatus.SWITCHED) && statusType != SVNStatusType.STATUS_NONE) {
        VirtualFile file = filePath.getVirtualFile();
        if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
          myChangelistBuilder.processChangeInList(createChange(SvnContentRevision.createBaseRevision(myVcs, filePath,
                                                                                                     status.getCommittedRevision()),
                                                   CurrentContentRevision.create(filePath), FileStatus.MODIFIED, status), changeListNameFromStatus(status),
                                                  SvnVcs.getKey());
        } else if (status.getTreeConflict() != null) {
          myChangelistBuilder.processChange(createChange(SvnContentRevision.createBaseRevision(myVcs, filePath,
                                                                                               status.getCommittedRevision()),
                                                   CurrentContentRevision.create(filePath), FileStatus.MODIFIED, status), SvnVcs.getKey());
        }
        checkSwitched(filePath, myChangelistBuilder, status, fStatus);
      }
    }
  }

  private void checkSwitched(final FilePath filePath, final ChangelistBuilder builder, final SVNStatus status,
                             final FileStatus convertedStatus) {
    if (status.isSwitched() || (convertedStatus == FileStatus.SWITCHED)) {
      final VirtualFile virtualFile = filePath.getVirtualFile();
      if (virtualFile == null) return;
      final String switchUrl = status.getURL().toString();
      final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsRootFor(virtualFile);
      if (vcsRoot != null) {  // it will be null if we walked into an excluded directory
        String baseUrl = null;
        try {
          baseUrl = myBranchConfigurationManager.get(vcsRoot).getBaseName(switchUrl);
        }
        catch (VcsException e) {
          LOG.info(e);
        }
        builder.processSwitchedFile(virtualFile, baseUrl == null ? switchUrl : baseUrl, true);
      }
    }
  }

  /**
   * Ensures that the contents of the 'entries' file is cached in the VFS, so that the VFS will send
   * correct events when the 'entries' file is changed externally (to be received by SvnEntriesFileListener)
   *
   * @param filePath the path of a changed file.
   */
  private static void loadEntriesFile(final FilePath filePath) {
    final FilePath parentPath = filePath.getParentPath();
    if (parentPath == null) {
      return;
    }
    File svnSubdirectory = new File(parentPath.getIOFile(), SvnUtil.SVN_ADMIN_DIR_NAME);
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

  // seems here we can only have a tree conflict; which can be marked on either path (?)
  // .. ok try to merge states
  Change createMovedChange(final ContentRevision before, final ContentRevision after, final SVNStatus copiedStatus,
                                   final SVNStatus deletedStatus) {
    return new ConflictedSvnChange(before, after, ConflictState.mergeState(getState(copiedStatus), getState(deletedStatus)),
                                  ((copiedStatus != null) && (copiedStatus.getTreeConflict() != null)) ? after.getFile() : before.getFile());
  }

  private Change createChange(final ContentRevision before, final ContentRevision after, final FileStatus fStatus, final SVNStatus svnStatus) {
    return new ConflictedSvnChange(before, after, fStatus, getState(svnStatus), after == null ? before.getFile() : after.getFile());
  }

  private LocallyDeletedChange createLocallyDeletedChange(@NotNull FilePath filePath, final SVNStatus status) {
    return new SvnLocallyDeletedChange(filePath, getState(status));
  }

  private ConflictState getState(@Nullable final SVNStatus svnStatus) {
    if (svnStatus == null) {
      return ConflictState.none;
    }

    final boolean treeConflict = svnStatus.getTreeConflict() != null;
    final boolean textConflict = SVNStatusType.STATUS_CONFLICTED == svnStatus.getContentsStatus();
    final boolean propertyConflict = SVNStatusType.STATUS_CONFLICTED == svnStatus.getPropertiesStatus();
    if (treeConflict) {
      reportTreeConflict(svnStatus);
    }

    return ConflictState.getInstance(treeConflict, textConflict, propertyConflict);
  }

  @Nullable
  private static String changeListNameFromStatus(final SVNStatus status) {
    if (WorkingCopyFormat.getInstance(status.getWorkingCopyFormat()).supportsChangelists()) {
      if (SVNNodeKind.FILE.equals(status.getKind())) {
        final String clName = status.getChangelistName();
        return (clName == null) ? null : clName;
      }
    }
    // always null for earlier versions
    return null;
  }
}
