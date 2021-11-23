// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.history.PropertyRevision;
import org.jetbrains.idea.svn.history.SvnLazyPropertyContentRevision;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.getRelativePath;

class SvnChangeProviderContext implements StatusReceiver {
  private static final Logger LOG = Logger.getInstance(SvnChangeProviderContext.class);

  @NotNull private final ChangelistBuilder myChangelistBuilder;
  @NotNull private final List<SvnChangedFile> myCopiedFiles = new ArrayList<>();
  @NotNull private final List<SvnChangedFile> myDeletedFiles = new ArrayList<>();
  // for files moved in a subtree, which were the targets of merge (for instance).
  @NotNull private final Map<String, Status> myTreeConflicted = new HashMap<>();
  @NotNull private final Map<FilePath, Url> myCopyFromURLs = new HashMap<>();
  @NotNull private final SvnVcs myVcs;
  private final SvnBranchConfigurationManager myBranchConfigurationManager;
  @NotNull private final List<File> filesToRefresh = new ArrayList<>();

  @Nullable private final ProgressIndicator myProgress;

  SvnChangeProviderContext(@NotNull SvnVcs vcs, @NotNull ChangelistBuilder changelistBuilder, @Nullable ProgressIndicator progress) {
    myVcs = vcs;
    myChangelistBuilder = changelistBuilder;
    myProgress = progress;
    myBranchConfigurationManager = SvnBranchConfigurationManager.getInstance(myVcs.getProject());
  }

  @Override
  public void process(FilePath path, Status status) throws SvnBindException {
    if (status != null) {
      processStatusFirstPass(path, status);
    }
  }

  @Override
  public void processIgnored(@NotNull FilePath path) {
    myChangelistBuilder.processIgnoredFile(path);
  }

  @Override
  public void processUnversioned(@NotNull FilePath path) {
    myChangelistBuilder.processUnversionedFile(path);
  }

  @Override
  public void processCopyRoot(VirtualFile file, Url url, WorkingCopyFormat format, Url rootURL) {
  }

  @Override
  public void bewareRoot(VirtualFile vf, Url url) {
  }

  @Override
  public void finish() {
    LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh, true, false, null);
  }

  @NotNull
  public ChangelistBuilder getBuilder() {
    return myChangelistBuilder;
  }

  public void reportTreeConflict(@NotNull Status status) {
    myTreeConflicted.put(status.getFile().getAbsolutePath(), status);
  }

  @Nullable
  public Status getTreeConflictStatus(@NotNull File file) {
    return myTreeConflicted.get(file.getAbsolutePath());
  }

  @NotNull
  public List<SvnChangedFile> getCopiedFiles() {
    return myCopiedFiles;
  }

  @NotNull
  public List<SvnChangedFile> getDeletedFiles() {
    return myDeletedFiles;
  }

  public boolean isDeleted(@NotNull FilePath path) {
    for (SvnChangedFile deletedFile : myDeletedFiles) {
      if (Comparing.equal(path, deletedFile.getFilePath())) {
        return true;
      }
    }
    return false;
  }

  public void checkCanceled() {
    if (myProgress != null) {
      myProgress.checkCanceled();
    }
  }

  /**
   * If the specified filepath or its parent was added with history, returns the URL of the copy source for this filepath.
   *
   * @param filePath the original filepath
   * @return the copy source url, or null if the file isn't a copy of anything
   */
  @Nullable
  public Url getParentCopyFromURL(@NotNull FilePath filePath) throws SvnBindException {
    Url result = null;
    FilePath parent = filePath;

    while (parent != null && !myCopyFromURLs.containsKey(parent)) {
      parent = parent.getParentPath();
    }

    if (parent != null) {
      Url copyFromUrl = myCopyFromURLs.get(parent);
      result = parent == filePath ? copyFromUrl : append(copyFromUrl, getRelativePath(parent.getPath(), filePath.getPath()));
    }

    return result;
  }

  public void addCopiedFile(@NotNull FilePath filePath, @NotNull Status status, @NotNull Url copyFromURL) {
    myCopiedFiles.add(new SvnChangedFile(filePath, status, copyFromURL));
    ContainerUtil.putIfNotNull(filePath, status.getCopyFromUrl(), myCopyFromURLs);
  }

  void processStatusFirstPass(@NotNull FilePath filePath, @NotNull Status status) throws SvnBindException {
    if (status.getRemoteLock() != null) {
      myChangelistBuilder.processLogicallyLockedFolder(filePath.getVirtualFile(), status.getRemoteLock().toLogicalLock(false));
    }
    if (status.getLocalLock() != null) {
      myChangelistBuilder.processLogicallyLockedFolder(filePath.getVirtualFile(), status.getLocalLock().toLogicalLock(true));
    }
    if (filePath.isDirectory() && status.isWorkingCopyLocked()) {
      myChangelistBuilder.processLockedFolder(filePath.getVirtualFile());
    }
    if (status.is(StatusType.STATUS_ADDED, StatusType.STATUS_MODIFIED, StatusType.STATUS_REPLACED) && isPossibleMove(filePath, status)) {
      addCopiedFile(filePath, status, status.getCopyFromUrl());
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      myDeletedFiles.add(new SvnChangedFile(filePath, status));
    }
    else {
      Url parentCopyFromURL = getParentCopyFromURL(filePath);
      if (parentCopyFromURL != null) {
        addCopiedFile(filePath, status, parentCopyFromURL);
      }
      else {
        processStatus(filePath, status);
      }
    }
  }

  private boolean isPossibleMove(@NotNull FilePath filePath, @NotNull Status status) {
    WorkingCopyFormat format = myVcs.getWorkingCopyFormat(filePath.getIOFile());

    if (format.isOrGreater(WorkingCopyFormat.ONE_DOT_EIGHT)) {
      return status.getMovedFrom() != null && status.getCopyFromUrl() != null;
    }

    return status.getCopyFromUrl() != null;
  }

  void processStatus(@NotNull FilePath filePath, @NotNull Status status) {
    WorkingCopyFormat format = myVcs.getWorkingCopyFormat(filePath.getIOFile());
    if (!WorkingCopyFormat.UNKNOWN.equals(format) && format.less(WorkingCopyFormat.ONE_DOT_SEVEN)) {
      loadEntriesFile(filePath);
    }

    FileStatus fStatus = Status.convertStatus(status);

    if (status.is(StatusType.STATUS_UNVERSIONED, StatusType.STATUS_NONE)) {
      final VirtualFile file = filePath.getVirtualFile();
      if (file != null) {
        myChangelistBuilder.processUnversionedFile(filePath);
      }
    }
    else if (status.is(StatusType.STATUS_MISSING)) {
      myChangelistBuilder.processLocallyDeletedFile(new SvnLocallyDeletedChange(filePath, getState(status)));
    }
    else if (status.is(StatusType.STATUS_ADDED)) {
      processChangeInList(null, CurrentContentRevision.create(filePath), fStatus, status);
    }
    else if (status.is(StatusType.STATUS_CONFLICTED, StatusType.STATUS_MODIFIED, StatusType.STATUS_REPLACED) ||
             status.isProperty(StatusType.STATUS_MODIFIED, StatusType.STATUS_CONFLICTED)) {
      processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, status), CurrentContentRevision.create(filePath), fStatus,
                          status);
      checkSwitched(filePath, status, fStatus);
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, status), null, fStatus, status);
    }
    else if (status.is(StatusType.STATUS_IGNORED)) {
      VirtualFile file = filePath.getVirtualFile();
      if (file == null) {
        file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.getPath());
      }
      if (file == null) {
        LOG.error("No virtual file for ignored file: " + filePath.getPresentableUrl() + ", isNonLocal: " + filePath.isNonLocal());
      }
      else if (!myVcs.isWcRoot(filePath)) {
        myChangelistBuilder.processIgnoredFile(filePath);
      }
    }
    else if (fStatus == FileStatus.NOT_CHANGED || fStatus == FileStatus.SWITCHED) {
      VirtualFile file = filePath.getVirtualFile();
      if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
        processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, status), CurrentContentRevision.create(filePath),
                            FileStatus.MODIFIED, status);
      }
      else if (status.getTreeConflict() != null) {
        myChangelistBuilder.processChange(createChange(SvnContentRevision.createBaseRevision(myVcs, filePath, status),
                                                       CurrentContentRevision.create(filePath), FileStatus.MODIFIED, status),
                                          SvnVcs.getKey());
      }
      checkSwitched(filePath, status, fStatus);
    }
  }

  public void addModifiedNotSavedChange(@NotNull VirtualFile file) {
    final FilePath filePath = VcsUtil.getFilePath(file);
    final Info svnInfo = myVcs.getInfo(file);

    if (svnInfo != null) {
      Status.Builder svnStatus = new Status.Builder(virtualToIoFile(file));
      svnStatus.setRevision(svnInfo.getRevision());
      svnStatus.setNodeKind(NodeKind.from(filePath.isDirectory()));
      processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, svnInfo.getRevision()),
                          CurrentContentRevision.create(filePath), FileStatus.MODIFIED, svnStatus.build());
    }
  }

  private void processChangeInList(@Nullable ContentRevision beforeRevision,
                                   @Nullable ContentRevision afterRevision,
                                   @NotNull FileStatus fileStatus,
                                   @NotNull Status status) {
    Change change = createChange(beforeRevision, afterRevision, fileStatus, status);

    myChangelistBuilder.processChangeInList(change, SvnUtil.getChangelistName(status), SvnVcs.getKey());
  }

  private void checkSwitched(@NotNull FilePath filePath, @NotNull Status status, @NotNull FileStatus convertedStatus) {
    if (status.isSwitched() || (convertedStatus == FileStatus.SWITCHED)) {
      final VirtualFile virtualFile = filePath.getVirtualFile();
      if (virtualFile == null) return;
      Url switchUrl = status.getUrl();
      final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsRootFor(virtualFile);
      if (vcsRoot != null) {  // it will be null if we walked into an excluded directory
        String baseUrl = myBranchConfigurationManager.get(vcsRoot).getBaseName(switchUrl);
        myChangelistBuilder.processSwitchedFile(virtualFile, baseUrl == null ? switchUrl.toDecodedString() : baseUrl, true);
      }
    }
  }

  /**
   * Ensures that the contents of the 'entries' file is cached in the VFS, so that the VFS will send
   * correct events when the 'entries' file is changed externally (to be received by SvnEntriesFileListener)
   *
   * @param filePath the path of a changed file.
   */
  private void loadEntriesFile(@NotNull FilePath filePath) {
    final FilePath parentPath = filePath.getParentPath();
    if (parentPath == null) {
      return;
    }
    refreshDotSvnAndEntries(parentPath);
    if (filePath.isDirectory()) {
      refreshDotSvnAndEntries(filePath);
    }
  }

  private void refreshDotSvnAndEntries(@NotNull FilePath filePath) {
    final File svn = new File(filePath.getPath(), SvnUtil.SVN_ADMIN_DIR_NAME);

    filesToRefresh.add(svn);
    filesToRefresh.add(new File(svn, SvnUtil.ENTRIES_FILE_NAME));
  }

  // seems here we can only have a tree conflict; which can be marked on either path (?)
  // .. ok try to merge states
  @NotNull
  Change createMovedChange(@NotNull ContentRevision before,
                           @NotNull ContentRevision after,
                           @Nullable Status copiedStatus,
                           @NotNull Status deletedStatus) {
    // todo no convertion needed for the contents status?
    ConflictedSvnChange change =
      new ConflictedSvnChange(before, after, ConflictState.mergeState(getState(copiedStatus), getState(deletedStatus)),
                              ((copiedStatus != null) && (copiedStatus.getTreeConflict() != null)) ? after.getFile() : before.getFile());
    change.setBeforeDescription(deletedStatus.getTreeConflict());
    if (copiedStatus != null) {
      change.setAfterDescription(copiedStatus.getTreeConflict());
      patchWithPropertyChange(change, copiedStatus, deletedStatus);
    }

    return change;
  }

  @NotNull
  private Change createChange(@Nullable ContentRevision before,
                              @Nullable ContentRevision after,
                              @NotNull FileStatus fStatus,
                              @NotNull Status svnStatus) {
    ConflictedSvnChange change =
      new ConflictedSvnChange(before, after, fStatus, getState(svnStatus), after == null ? before.getFile() : after.getFile());

    change.setIsPhantom(svnStatus.is(StatusType.STATUS_DELETED) && !svnStatus.getRevision().isValid());
    change.setBeforeDescription(svnStatus.getTreeConflict());
    patchWithPropertyChange(change, svnStatus, null);

    return change;
  }

  private void patchWithPropertyChange(@NotNull Change change, @NotNull Status svnStatus, @Nullable Status deletedStatus) {
    if (!svnStatus.isProperty(StatusType.STATUS_CONFLICTED, StatusType.CHANGED, StatusType.STATUS_ADDED, StatusType.STATUS_DELETED,
                              StatusType.STATUS_MODIFIED, StatusType.STATUS_REPLACED, StatusType.MERGED)) {
      return;
    }

    PropertyRevision before = createBeforePropertyRevision(change, svnStatus, deletedStatus);
    PropertyRevision after = createAfterPropertyRevision(change, svnStatus);
    FileStatus status = deletedStatus != null ? FileStatus.MODIFIED : Status.convertPropertyStatus(svnStatus.getPropertyStatus());

    change.addAdditionalLayerElement(SvnChangeProvider.PROPERTY_LAYER, new Change(before, after, status));
  }

  @Nullable
  private PropertyRevision createBeforePropertyRevision(@NotNull Change change, @NotNull Status svnStatus, @Nullable Status deletedStatus) {
    if (svnStatus.isProperty(StatusType.STATUS_ADDED) && deletedStatus == null) return null;

    ContentRevision before = change.getBeforeRevision();
    if (before == null) return null;

    FilePath path = ChangesUtil.getFilePath(change);
    File file = deletedStatus != null ? deletedStatus.getFile() : path.getIOFile();
    Target target = Target.on(file, Revision.BASE);
    return new SvnLazyPropertyContentRevision(myVcs, path, before.getRevisionNumber(), target);
  }

  @Nullable
  private PropertyRevision createAfterPropertyRevision(@NotNull Change change, @NotNull Status svnStatus) {
    if (svnStatus.isProperty(StatusType.STATUS_DELETED)) return null;

    ContentRevision after = change.getAfterRevision();
    if (after == null) return null;

    FilePath path = ChangesUtil.getFilePath(change);
    Target target = Target.on(path.getIOFile(), Revision.WORKING);
    return new SvnLazyPropertyContentRevision(myVcs, path, after.getRevisionNumber(), target);
  }

  @NotNull
  private ConflictState getState(@Nullable Status svnStatus) {
    ConflictState result = svnStatus != null ? ConflictState.from(svnStatus) : ConflictState.none;

    if (result.isTree()) {
      //noinspection ConstantConditions
      reportTreeConflict(svnStatus);
    }

    return result;
  }
}
