// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
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
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.history.SimplePropertyRevision;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.actions.ShowPropertiesDiffAction.getPropertyList;

class SvnChangeProviderContext implements StatusReceiver {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnChangeProviderContext");

  @NotNull private final ChangelistBuilder myChangelistBuilder;
  @NotNull private final List<SvnChangedFile> myCopiedFiles = ContainerUtil.newArrayList();
  @NotNull private final List<SvnChangedFile> myDeletedFiles = ContainerUtil.newArrayList();
  // for files moved in a subtree, which were the targets of merge (for instance).
  @NotNull private final Map<String, Status> myTreeConflicted = ContainerUtil.newHashMap();
  @NotNull private final Map<FilePath, Url> myCopyFromURLs = ContainerUtil.newHashMap();
  @NotNull private final SvnVcs myVcs;
  private final SvnBranchConfigurationManager myBranchConfigurationManager;
  @NotNull private final List<File> filesToRefresh = ContainerUtil.newArrayList();

  @Nullable private final ProgressIndicator myProgress;

  public SvnChangeProviderContext(@NotNull SvnVcs vcs, @NotNull ChangelistBuilder changelistBuilder, @Nullable ProgressIndicator progress) {
    myVcs = vcs;
    myChangelistBuilder = changelistBuilder;
    myProgress = progress;
    myBranchConfigurationManager = SvnBranchConfigurationManager.getInstance(myVcs.getProject());
  }

  public void process(FilePath path, Status status) throws SvnBindException {
    if (status != null) {
      processStatusFirstPass(path, status);
    }
  }

  public void processIgnored(VirtualFile vFile) {
    myChangelistBuilder.processIgnoredFile(vFile);
  }

  public void processUnversioned(VirtualFile vFile) {
    myChangelistBuilder.processUnversionedFile(vFile);
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

      //noinspection ConstantConditions
      result = parent == filePath ? copyFromUrl : append(copyFromUrl, FileUtil.getRelativePath(parent.getIOFile(), filePath.getIOFile()));
    }

    return result;
  }

  public void addCopiedFile(@NotNull FilePath filePath, @NotNull Status status, @NotNull Url copyFromURL) {
    myCopiedFiles.add(new SvnChangedFile(filePath, status, copyFromURL));
    ContainerUtil.putIfNotNull(filePath, status.getCopyFromURL(), myCopyFromURLs);
  }

  void processStatusFirstPass(@NotNull FilePath filePath, @NotNull Status status) throws SvnBindException {
    if (status.getRemoteLock() != null) {
      myChangelistBuilder.processLogicallyLockedFolder(filePath.getVirtualFile(), status.getRemoteLock().toLogicalLock(false));
    }
    if (status.getLocalLock() != null) {
      myChangelistBuilder.processLogicallyLockedFolder(filePath.getVirtualFile(), status.getLocalLock().toLogicalLock(true));
    }
    if (filePath.isDirectory() && status.isLocked()) {
      myChangelistBuilder.processLockedFolder(filePath.getVirtualFile());
    }
    if ((status.is(StatusType.STATUS_ADDED) || StatusType.STATUS_MODIFIED.equals(status.getNodeStatus())) &&
        status.getCopyFromURL() != null) {
      addCopiedFile(filePath, status, status.getCopyFromURL());
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

  void processStatus(@NotNull FilePath filePath, @NotNull Status status) throws SvnBindException {
    WorkingCopyFormat format = myVcs.getWorkingCopyFormat(filePath.getIOFile());
    if (!WorkingCopyFormat.UNKNOWN.equals(format) && format.less(WorkingCopyFormat.ONE_DOT_SEVEN)) {
      loadEntriesFile(filePath);
    }

    FileStatus fStatus = SvnStatusConvertor.convertStatus(status);

    final StatusType statusType = status.getContentsStatus();
    if (status.is(StatusType.STATUS_UNVERSIONED, StatusType.UNKNOWN)) {
      final VirtualFile file = filePath.getVirtualFile();
      if (file != null) {
        myChangelistBuilder.processUnversionedFile(file);
      }
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
    else if (status.is(StatusType.STATUS_MISSING)) {
      myChangelistBuilder.processLocallyDeletedFile(new SvnLocallyDeletedChange(filePath, getState(status)));
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
        myChangelistBuilder.processIgnoredFile(filePath.getVirtualFile());
      }
    }
    else if ((fStatus == FileStatus.NOT_CHANGED || fStatus == FileStatus.SWITCHED) && statusType != StatusType.STATUS_NONE) {
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

  public void addModifiedNotSavedChange(@NotNull VirtualFile file) throws SvnBindException {
    final FilePath filePath = VcsUtil.getFilePath(file);
    final Info svnInfo = myVcs.getInfo(file);

    if (svnInfo != null) {
      final Status svnStatus = new Status();
      svnStatus.setRevision(svnInfo.getRevision());
      svnStatus.setKind(NodeKind.from(filePath.isDirectory()));
      processChangeInList(SvnContentRevision.createBaseRevision(myVcs, filePath, svnInfo.getRevision()),
                          CurrentContentRevision.create(filePath), FileStatus.MODIFIED, svnStatus);
    }
  }

  private void processChangeInList(@Nullable ContentRevision beforeRevision,
                                   @Nullable ContentRevision afterRevision,
                                   @NotNull FileStatus fileStatus,
                                   @NotNull Status status) throws SvnBindException {
    Change change = createChange(beforeRevision, afterRevision, fileStatus, status);

    myChangelistBuilder.processChangeInList(change, SvnUtil.getChangelistName(status), SvnVcs.getKey());
  }

  private void checkSwitched(@NotNull FilePath filePath, @NotNull Status status, @NotNull FileStatus convertedStatus) {
    if (status.isSwitched() || (convertedStatus == FileStatus.SWITCHED)) {
      final VirtualFile virtualFile = filePath.getVirtualFile();
      if (virtualFile == null) return;
      final String switchUrl = status.getURL().toString();
      final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsRootFor(virtualFile);
      if (vcsRoot != null) {  // it will be null if we walked into an excluded directory
        String baseUrl = myBranchConfigurationManager.get(vcsRoot).getBaseName(switchUrl);
        myChangelistBuilder.processSwitchedFile(virtualFile, baseUrl == null ? switchUrl : baseUrl, true);
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
                           @NotNull Status deletedStatus) throws SvnBindException {
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
                              @NotNull Status svnStatus) throws SvnBindException {
    ConflictedSvnChange change =
      new ConflictedSvnChange(before, after, fStatus, getState(svnStatus), after == null ? before.getFile() : after.getFile());

    change.setIsPhantom(StatusType.STATUS_DELETED.equals(svnStatus.getNodeStatus()) && !svnStatus.getRevision().isValid());
    change.setBeforeDescription(svnStatus.getTreeConflict());
    patchWithPropertyChange(change, svnStatus, null);

    return change;
  }

  private void patchWithPropertyChange(@NotNull Change change, @NotNull Status svnStatus, @Nullable Status deletedStatus)
    throws SvnBindException {
    if (svnStatus.isProperty(StatusType.STATUS_CONFLICTED, StatusType.CHANGED, StatusType.STATUS_ADDED, StatusType.STATUS_DELETED,
                             StatusType.STATUS_MODIFIED, StatusType.STATUS_REPLACED, StatusType.MERGED)) {
      change.addAdditionalLayerElement(SvnChangeProvider.PROPERTY_LAYER, createPropertyChange(change, svnStatus, deletedStatus));
    }
  }

  @NotNull
  private Change createPropertyChange(@NotNull Change change, @NotNull Status svnStatus, @Nullable Status deletedStatus)
    throws SvnBindException {
    final File ioFile = ChangesUtil.getFilePath(change).getIOFile();
    final File beforeFile = deletedStatus != null ? deletedStatus.getFile() : ioFile;

    // TODO: There are cases when status output is like (on newly added file with some properties that is locally deleted)
    // <entry path="some_path"> <wc-status item="missing" revision="-1" props="modified"> </wc-status> </entry>
    // TODO: For such cases in current logic we'll have Change with before revision containing Revision.UNDEFINED
    // TODO: Analyze if this logic is OK or we should update flow somehow (for instance, to have null before revision)
    ContentRevision beforeRevision =
      !svnStatus.isProperty(StatusType.STATUS_ADDED) || deletedStatus != null ? createPropertyRevision(change, beforeFile, true) : null;
    ContentRevision afterRevision = !svnStatus.isProperty(StatusType.STATUS_DELETED) ? createPropertyRevision(change, ioFile, false) : null;
    FileStatus status =
      deletedStatus != null ? FileStatus.MODIFIED : SvnStatusConvertor.convertPropertyStatus(svnStatus.getPropertiesStatus());

    return new Change(beforeRevision, afterRevision, status);
  }

  @Nullable
  private ContentRevision createPropertyRevision(@NotNull Change change, @NotNull File file, boolean isBeforeRevision)
    throws SvnBindException {
    FilePath path = ChangesUtil.getFilePath(change);
    ContentRevision contentRevision = isBeforeRevision ? change.getBeforeRevision() : change.getAfterRevision();
    Revision revision = isBeforeRevision ? Revision.BASE : Revision.WORKING;

    return new SimplePropertyRevision(getPropertyList(myVcs, file, revision), path, getRevisionNumber(contentRevision));
  }

  @Nullable
  private static String getRevisionNumber(@Nullable ContentRevision revision) {
    return revision != null ? revision.getRevisionNumber().asString() : null;
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
