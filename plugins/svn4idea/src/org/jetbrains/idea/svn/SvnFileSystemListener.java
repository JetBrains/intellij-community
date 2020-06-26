// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.idea.svn;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.Functions;
import com.intellij.util.ThrowableConsumer;
import com.intellij.vcsUtil.ActionWithTempFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.map;

public class SvnFileSystemListener implements LocalFileOperationsHandler, Disposable, CommandListener {
  private static final Logger LOG = Logger.getInstance(SvnFileSystemListener.class);

  private static class AddedFileInfo {
    private final VirtualFile myDir;
    private final String myName;
    @Nullable private final File myCopyFrom;
    private final boolean myRecursive;

    AddedFileInfo(@NotNull VirtualFile dir, @NotNull String name, @Nullable final File copyFrom, boolean recursive) {
      myDir = dir;
      myName = name;
      myCopyFrom = copyFrom;
      myRecursive = recursive;
    }
  }

  private static final class MovedFileInfo {
    private final File mySrc;
    private final File myDst;

    private MovedFileInfo(@NotNull File src, @NotNull File dst) {
      mySrc = src;
      myDst = dst;
    }
  }

  @NotNull private final SvnVcs myVcs;

  private final List<AddedFileInfo> myAddedFiles = new ArrayList<>();
  private final List<File> myDeletedFiles = new ArrayList<>();
  private final List<MovedFileInfo> myMovedFiles = new ArrayList<>();
  private final List<VcsException> myMoveExceptions = new ArrayList<>();
  private final List<VirtualFile> myFilesToRefresh = new ArrayList<>();
  @Nullable private File myStorageForUndo;
  private final List<Couple<File>> myUndoStorageContents = new ArrayList<>();
  private boolean myUndoingMove = false;

  private boolean myIsInCommand;
  private boolean myIsOperationStarted;

  public SvnFileSystemListener(@NotNull SvnVcs vcs) {
    myVcs = vcs;

    LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(this);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(CommandListener.TOPIC, this);
  }

  @Override
  public void dispose() {
    LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(this);
  }

  private boolean isMyVcs(@NotNull VirtualFile file) {
    return VcsUtil.isFileForVcs(file, myVcs.getProject(), myVcs);
  }

  @NotNull
  private static VcsException handleMoveException(@NotNull VcsException e) {
    return e instanceof SvnBindException && ((SvnBindException)e).contains(ErrorCode.ENTRY_EXISTS) ? createMoveTargetExistsError(e) : e;
  }

  @NotNull
  private static VcsException createMoveTargetExistsError(@NotNull Exception e) {
    return new VcsException(Arrays.asList("Target of move operation is already under version control.",
                                          "Subversion move had not been performed. ", e.getMessage()));
  }

  @Override
  @Nullable
  public File copy(@NotNull final VirtualFile file, @NotNull final VirtualFile toDir, @NotNull final String copyName) {
    if (!isMyVcs(toDir)) return null;

    startOperation(toDir);
    File srcFile = virtualToIoFile(file);
    File destFile = new File(virtualToIoFile(toDir), copyName);
    if (!SvnUtil.isSvnVersioned(myVcs, destFile.getParentFile()) && !isPendingAdd(toDir)) {
      return null;
    }

    if (!SvnUtil.isSvnVersioned(myVcs, srcFile.getParentFile())) {
      myAddedFiles.add(new AddedFileInfo(toDir, copyName, null, false));
      return null;
    }

    final Status fileStatus = getFileStatus(srcFile);
    if (fileStatus != null && fileStatus.is(StatusType.STATUS_ADDED)) {
      myAddedFiles.add(new AddedFileInfo(toDir, copyName, null, false));
      return null;
    }

    if (sameRoot(file.getParent(), toDir)) {
      myAddedFiles.add(new AddedFileInfo(toDir, copyName, srcFile, false));
      return null;
    }

    myAddedFiles.add(new AddedFileInfo(toDir, copyName, null, false));
    return null;
  }

  private boolean sameRoot(@NotNull VirtualFile srcDir, @NotNull VirtualFile dstDir) {
    final String srcUUID = getRepositoryUUID(srcDir);
    final String dstUUID = getRepositoryUUID(dstDir);

    return srcUUID != null && dstUUID != null && srcUUID.equals(dstUUID);
  }

  /**
   * passed dir must be under VC control (it is assumed)
   */
  @Nullable
  private String getRepositoryUUID(@NotNull VirtualFile dir) {
    try {
      final Info info1 = new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() {
          myT = myVcs.getInfo(virtualToIoFile(dir));
        }
      }.compute();
      if (info1 == null || info1.getRepositoryId() == null) {
        // go deeper if current parent was added (if parent was added, it theoretically could NOT know its repo UUID)
        final VirtualFile parent = dir.getParent();
        if (parent == null) {
          return null;
        }
        if (isPendingAdd(parent)) {
          return getRepositoryUUID(parent);
        }
      }
      else {
        return info1.getRepositoryId();
      }
    }
    catch (VcsException e) {
      // go to return default
    }
    return null;
  }

  @Override
  public boolean move(@NotNull VirtualFile file, @NotNull VirtualFile toDir) {
    if (!isMyVcs(toDir)) return false;

    startOperation(toDir);
    if (!isMyVcs(file)) return createItem(toDir, file.getName(), file.isDirectory(), true);

    File srcFile = getIOFile(file);
    File dstFile = new File(getIOFile(toDir), file.getName());

    // save all documents here when !myMovedFiles.isEmpty() deletes these files from VFS
    // these leads to psi invalidation during refactoring inside write action
    // FileDocumentManager.getInstance().saveAllDocuments();
    if (isPendingAdd(toDir)) {
      myMovedFiles.add(new MovedFileInfo(srcFile, dstFile));
      return true;
    }
    else {
      myFilesToRefresh.add(file.getParent());
      myFilesToRefresh.add(toDir);
      return doMove(srcFile, dstFile);
    }
  }

  @Override
  public boolean rename(@NotNull VirtualFile file, @NotNull String newName) {
    if (!isMyVcs(file)) return false;

    startOperation(file);
    File srcFile = getIOFile(file);
    File dstFile = new File(srcFile.getParentFile(), newName);

    // save all documents here when !myMovedFiles.isEmpty() deletes these files from VFS
    // these leads to psi invalidation during refactoring inside write action
    // FileDocumentManager.getInstance().saveAllDocuments();
    myFilesToRefresh.add(file.getParent());
    return doMove(srcFile, dstFile);
  }

  private boolean doMove(@NotNull File src, @NotNull File dst) {
    try {
      final boolean isUndo = isUndo();
      final String list = isUndo ? null : SvnChangelistListener.getCurrentMapping(myVcs, src);

      WorkingCopyFormat format = myVcs.getWorkingCopyFormat(src);
      final boolean is17OrLater = format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN);
      if (is17OrLater) {
        Status srcStatus = getFileStatus(src);
        if (isUnversioned(srcStatus) && (isUnversioned(dst.getParentFile()) || isUnversioned(dst)) ||
            for17move(src, dst, isUndo, srcStatus)) {
          return false;
        }
      }
      else {
        if (for16move(dst, isUndo)) return false;
      }

      if (!isUndo && list != null) {
        SvnChangelistListener.putUnderList(myVcs, list, dst);
      }
    }
    catch (VcsException e) {
      myMoveExceptions.add(handleMoveException(e));
      return false;
    }
    return true;
  }

  private static boolean isUnversioned(@Nullable Status status) {
    return status == null || status.is(StatusType.STATUS_UNVERSIONED);
  }

  private boolean isUnversioned(@NotNull File file) {
    return isUnversioned(getFileStatus(file));
  }

  private boolean for17move(final File src, final File dst, boolean undo, Status srcStatus) throws VcsException {
    if (srcStatus != null && srcStatus.getCopyFromUrl() == null) {
      undo = false;
    }
    if (undo) {
      myUndoingMove = true;
      boolean isCaseOnlyMove = FileUtil.filesEqual(src, dst);
      createRevertAction(isCaseOnlyMove ? src : dst, true).execute();
      copyUnversionedMembersOfDirectory(src, dst);
      if (isUnversioned(srcStatus)) {
        FileUtil.delete(src);
      }
      else {
        createRevertAction(isCaseOnlyMove ? dst : src, true).execute();
      }
      restoreFromUndoStorage(dst);
    }
    else {
      if (doUsualMove(src)) return true;
      // check destination directory
      if (isUnversioned(dst.getParentFile())) {
        try {
          FileUtil.copyFileOrDir(src, dst);
        }
        catch (IOException e) {
          throw new SvnBindException(e);
        }
        createDeleteAction(src, true).execute();
        return false;
      }
      moveFileWithSvn(myVcs, src, dst);
    }
    return false;
  }

  public static void moveFileWithSvn(final SvnVcs vcs, final File src, final File dst) throws VcsException {
    new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws VcsException {
        vcs.getFactory(src).createCopyMoveClient().copy(src, dst, false, true);
      }
    }.execute();
  }

  private static void copyUnversionedMembersOfDirectory(final File src, final File dst) throws SvnBindException {
    if (src.isDirectory()) {
      final SvnBindException[] exc = new SvnBindException[1];
      FileUtil.processFilesRecursively(src, file -> {
        String relativePath = FileUtil.getRelativePath(src, file);
        File newFile = new File(dst, relativePath);
        if (!newFile.exists()) {
          try {
            FileUtil.copyFileOrDir(src, dst);
          }
          catch (IOException e) {
            exc[0] = new SvnBindException(e);
            return false;
          }
        }
        return true;
      });
      if (exc[0] != null) {
        throw exc[0];
      }
    }
  }

  private boolean doUsualMove(File src) {
    // if src is not under version control, do usual move.
    Status srcStatus = getFileStatus(src);
    return srcStatus == null ||
           srcStatus.is(StatusType.STATUS_UNVERSIONED, StatusType.STATUS_OBSTRUCTED, StatusType.STATUS_MISSING, StatusType.STATUS_EXTERNAL);
  }

  private boolean for16move(final File dst, final boolean undo) {
    if (undo) {
      myUndoingMove = true;
      restoreFromUndoStorage(dst);
    }

    // TODO: Implement svn 1.6 support for command line.
    return true;
  }

  private void restoreFromUndoStorage(final File dst) {
    String normPath = FileUtil.toSystemIndependentName(dst.getPath());
    for (Iterator<Couple<File>> it = myUndoStorageContents.iterator(); it.hasNext(); ) {
      Couple<File> e = it.next();
      final String p = FileUtil.toSystemIndependentName(e.first.getPath());
      if (p.startsWith(normPath)) {
        try {
          FileUtil.rename(e.second, e.first);
        }
        catch (IOException ex) {
          LOG.error(ex);
          FileUtil.asyncDelete(e.second);
        }
        it.remove();
      }
    }
    if (myStorageForUndo != null) {
      final File[] files = myStorageForUndo.listFiles();
      if (files == null || files.length == 0) {
        FileUtil.asyncDelete(myStorageForUndo);
        myStorageForUndo = null;
      }
    }
  }


  @Override
  public boolean createFile(@NotNull VirtualFile dir, @NotNull String name) {
    if (!isMyVcs(dir)) return false;

    startOperation(dir);
    return createItem(dir, name, false, false);
  }

  @Override
  public boolean createDirectory(@NotNull VirtualFile dir, @NotNull String name) {
    if (!isMyVcs(dir)) return false;

    startOperation(dir);
    return createItem(dir, name, true, false);
  }

  /**
   * delete file or directory (both 'undo' and 'do' modes)
   * unversioned: do nothing, return false
   * obstructed: do nothing, return false
   * external or wc root: do nothing, return false
   * <p/>
   * missing: schedule for deletion
   * versioned: schedule for deletion, return true
   * added: schedule for deletion (make unversioned), return true
   * copied, but not scheduled: schedule for deletion, return true
   * replaced: schedule for deletion, return true
   * <p/>
   * deleted: do nothing, return true (strange)
   */
  @Override
  public boolean delete(@NotNull VirtualFile file) {
    if (!isMyVcs(file)) return false;

    startOperation(file);
    if (SvnUtil.isAdminDirectory(file)) return true;

    final VcsShowConfirmationOption.Value value = myVcs.getDeleteConfirmation().getValue();
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(value)) return false;

    final File ioFile = getIOFile(file);
    if (!SvnUtil.isSvnVersioned(myVcs, ioFile.getParentFile()) || SvnUtil.isWorkingCopyRoot(ioFile)) {
      return false;
    }

    Status status = getFileStatus(ioFile);

    if (status == null ||
        status.is(StatusType.STATUS_UNVERSIONED, StatusType.STATUS_OBSTRUCTED, StatusType.STATUS_EXTERNAL, StatusType.STATUS_IGNORED)) {
      return false;
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      if (isUndo()) {
        moveToUndoStorage(file);
      }
      return true;
    }
    else {
      if (isAboveSourceOfCopyOrMove(ioFile)) {
        myDeletedFiles.add(ioFile);
        return true;
      }
      if (status.is(StatusType.STATUS_ADDED)) {
        try {
          createRevertAction(ioFile, false).execute();
        }
        catch (VcsException e) {
          // ignore
        }
      }
      else {
        myDeletedFiles.add(ioFile);
        // packages deleted from disk should not be deleted from svn (IDEADEV-16066)
        if (file.isDirectory() || isUndo()) return true;
      }
      return false;
    }
  }

  @NotNull
  private RepeatSvnActionThroughBusy createRevertAction(@NotNull final File file, final boolean recursive) {
    return new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws VcsException {
        myVcs.getFactory(file).createRevertClient().revert(Collections.singletonList(file), Depth.allOrFiles(recursive), null);
      }
    };
  }

  @NotNull
  private RepeatSvnActionThroughBusy createDeleteAction(@NotNull final File file, final boolean force) {
    return new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws VcsException {
        myVcs.getFactory(file).createDeleteClient().delete(file, force, false, null);
      }
    };
  }

  private boolean isAboveSourceOfCopyOrMove(File ioFile) {
    for (MovedFileInfo file : myMovedFiles) {
      if (FileUtil.isAncestor(ioFile, file.mySrc, false)) return true;
    }
    for (AddedFileInfo info : myAddedFiles) {
      if (info.myCopyFrom != null && FileUtil.isAncestor(ioFile, info.myCopyFrom, false)) return true;
    }
    return false;
  }

  private void moveToUndoStorage(final VirtualFile file) {
    if (myStorageForUndo == null) {
      try {
        myStorageForUndo = FileUtil.createTempDirectory("svnUndoStorage", "");
      }
      catch (IOException e) {
        LOG.error(e);
        return;
      }
    }
    final File tmpFile = FileUtil.findSequentNonexistentFile(myStorageForUndo, "tmp", "");
    myUndoStorageContents.add(0, Couple.of(virtualToIoFile(file), tmpFile));
    virtualToIoFile(file).renameTo(tmpFile);
  }

  /**
   * add file or directory:
   * <p/>
   * parent directory is:
   * unversioned: do nothing, return false
   * versioned:
   * entry is:
   * null: create entry, schedule for addition
   * missing: do nothing, return false
   * deleted, 'do' mode: try to create entry and it schedule for addition if kind is the same, otherwise do nothing, return false.
   * deleted: 'undo' mode: try to revert non-recursively, if kind is the same, otherwise do nothing, return false.
   * anything else: return false.
   */
  private boolean createItem(VirtualFile dir, String name, boolean directory, final boolean recursive) {
    final VcsShowConfirmationOption.Value value = myVcs.getAddConfirmation().getValue();
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(value)) return false;

    if (isUndo() && SvnUtil.isAdminDirectory(dir, name)) {
      return false;
    }
    File ioDir = getIOFile(dir);
    boolean pendingAdd = isPendingAdd(dir);
    if (!SvnUtil.isSvnVersioned(myVcs, ioDir) && !pendingAdd) {
      return false;
    }
    final File targetFile = new File(ioDir, name);
    Status status = getFileStatus(targetFile);

    if (status == null || status.is(StatusType.STATUS_NONE, StatusType.STATUS_UNVERSIONED)) {
      myAddedFiles.add(new AddedFileInfo(dir, name, null, recursive));
      return false;
    }
    else if (status.is(StatusType.STATUS_MISSING)) {
      return false;
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      NodeKind kind = status.getNodeKind();
      // kind differs.
      if (directory && !kind.isDirectory() || !directory && !kind.isFile()) {
        return false;
      }
      try {
        if (isUndo()) {
          createRevertAction(targetFile, false).execute();
          return true;
        }
        myAddedFiles.add(new AddedFileInfo(dir, name, null, recursive));
        return false;
      }
      catch (VcsException e) {
        FileUtil.delete(targetFile);
        return false;
      }
    }
    return false;
  }

  private boolean isPendingAdd(final VirtualFile dir) {
    for (AddedFileInfo i : myAddedFiles) {
      if (Comparing.equal(i.myDir, dir.getParent()) && i.myName.equals(dir.getName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void commandStarted(@NotNull CommandEvent event) {
    myIsInCommand = true;
    myUndoingMove = false;

    if (myVcs.getProject() != event.getProject()) return;
    commandStarted();
  }

  private void commandStarted() {
    myUndoingMove = false;
    myMoveExceptions.clear();
  }

  @Override
  public void commandFinished(@NotNull CommandEvent event) {
    myIsInCommand = false;

    if (myVcs.getProject() != event.getProject()) return;
    commandFinished();
  }

  private void commandFinished() {
    checkOverwrites();
    if (!myAddedFiles.isEmpty()) {
      processAddedFiles();
    }
    processMovedFiles();
    if (!myDeletedFiles.isEmpty()) {
      processDeletedFiles();
    }

    if (!myMoveExceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(myVcs.getProject()).showErrors(myMoveExceptions, SvnBundle.message("move.files.errors.title"));
    }

    if (!myFilesToRefresh.isEmpty()) {
      refreshFiles();
    }
  }

  private void checkOverwrites() {
    if (myAddedFiles.isEmpty() || myDeletedFiles.isEmpty()) return;
    final Iterator<AddedFileInfo> iterator = myAddedFiles.iterator();
    while (iterator.hasNext()) {
      AddedFileInfo addedFileInfo = iterator.next();
      final File ioFile = new File(addedFileInfo.myDir.getPath(), addedFileInfo.myName);
      if (myDeletedFiles.remove(ioFile)) {
        iterator.remove();
      }
    }
  }

  private void refreshFiles() {
    final List<VirtualFile> toRefreshFiles = new ArrayList<>();
    final List<VirtualFile> toRefreshDirs = new ArrayList<>();
    for (VirtualFile file : myFilesToRefresh) {
      if (file == null) continue;
      if (file.isDirectory()) {
        toRefreshDirs.add(file);
      }
      else {
        toRefreshFiles.add(file);
      }
    }
    // if refresh asynchronously, local changes would also be notified that they are dirty asynchronously,
    // and commit could be executed while not all changes are visible
    filterOutInvalid(myFilesToRefresh);
    RefreshQueue.getInstance().refresh(true, true, () -> {
      if (myVcs.getProject().isDisposed()) return;
      filterOutInvalid(toRefreshFiles);
      filterOutInvalid(toRefreshDirs);

      final VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myVcs.getProject());
      vcsDirtyScopeManager.filesDirty(toRefreshFiles, toRefreshDirs);
    }, myFilesToRefresh);
    myFilesToRefresh.clear();
  }

  private static void filterOutInvalid(@NotNull Collection<VirtualFile> files) {
    for (Iterator<VirtualFile> iterator = files.iterator(); iterator.hasNext(); ) {
      VirtualFile file = iterator.next();

      if (file == null) {
        iterator.remove();
      }
      else if (!file.isValid() || !file.exists()) {
        LOG.info("Refresh root is not valid: " + file.getPath());
        iterator.remove();
      }
    }
  }

  private void processAddedFiles() {
    final List<VirtualFile> addedVFiles = new ArrayList<>();
    final Map<VirtualFile, File> copyFromMap = new HashMap<>();
    final Set<VirtualFile> recursiveItems = new HashSet<>();
    fillAddedFiles(addedVFiles, copyFromMap, recursiveItems);
    if (addedVFiles.isEmpty()) return;
    final VcsShowConfirmationOption.Value value = myVcs.getAddConfirmation().getValue();
    if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      // Current method could be invoked under write action (for instance, during project import). So we explicitly use
      // Application.invokeLater() in such cases to prevent deadlocks (while accessing vcs root mappings) and also not to show dialog under
      // write action.
      runNotUnderWriteAction(() -> {
        final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(myVcs.getProject());
        final Collection<VirtualFile> filesToProcess = promptAboutAddition(addedVFiles, value, vcsHelper);
        if (filesToProcess != null && !filesToProcess.isEmpty()) {
          final List<VcsException> exceptions = new ArrayList<>();
          runInBackground("Adding files to Subversion",
                          createAdditionRunnable(copyFromMap, filesToProcess, exceptions));
          if (!exceptions.isEmpty()) {
            vcsHelper.showErrors(exceptions, SvnBundle.message("add.files.errors.title"));
          }
        }
      });
    }
  }

  private void runNotUnderWriteAction(@NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed()) {
      application.invokeLater(runnable, myVcs.getProject().getDisposed());
    }
    else {
      runnable.run();
    }
  }

  private void runInBackground(final String name, final Runnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, name, false, myVcs.getProject());
    }
    else {
      runnable.run();
    }
  }

  private Runnable createAdditionRunnable(final Map<VirtualFile, File> copyFromMap,
                                          final Collection<? extends VirtualFile> filesToProcess,
                                          final List<? super VcsException> exceptions) {
    return () -> {
      for (VirtualFile file : filesToProcess) {
        final File ioFile = virtualToIoFile(file);
        try {
          final File copyFrom = copyFromMap.get(file);
          if (copyFrom != null) {
            try {
              new ActionWithTempFile(ioFile) {
                @Override
                protected void executeInternal() throws VcsException {
                  // not recursive
                  new RepeatSvnActionThroughBusy() {
                    @Override
                    protected void executeImpl() throws VcsException {
                      myVcs.getFactory(copyFrom).createCopyMoveClient().copy(copyFrom, ioFile, true, false);
                    }
                  }.execute();
                }
              }.execute();
            }
            catch (VcsException e) {
              exceptions.add(e);
            }
          }
          else {
            new RepeatSvnActionThroughBusy() {
              @Override
              protected void executeImpl() throws VcsException {
                myVcs.getFactory(ioFile).createAddClient().add(ioFile, null, false, false, true, null);
              }
            }.execute();
          }
          VcsDirtyScopeManager.getInstance(myVcs.getProject()).fileDirty(file);
        }
        catch (VcsException e) {
          exceptions.add(e);
        }
      }
    };
  }

  private Collection<VirtualFile> promptAboutAddition(List<VirtualFile> addedVFiles,
                                                      VcsShowConfirmationOption.Value value,
                                                      AbstractVcsHelper vcsHelper) {
    Collection<VirtualFile> filesToProcess;
    if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      filesToProcess = addedVFiles;
    }
    else {
      final String singleFilePrompt;
      if (addedVFiles.size() == 1 && addedVFiles.get(0).isDirectory()) {
        singleFilePrompt = SvnBundle.message("confirmation.text.add.dir");
      }
      else {
        singleFilePrompt = SvnBundle.message("confirmation.text.add.file");
      }
      filesToProcess = vcsHelper.selectFilesToProcess(addedVFiles, SvnBundle.message("confirmation.title.add.multiple.files"),
                                                      null,
                                                      SvnBundle.message("confirmation.title.add.file"), singleFilePrompt,
                                                      myVcs.getAddConfirmation());
    }
    return filesToProcess;
  }

  private void fillAddedFiles(List<? super VirtualFile> addedVFiles,
                              Map<VirtualFile, File> copyFromMap,
                              Set<? super VirtualFile> recursiveItems) {
    final Collection<AddedFileInfo> addedFileInfos = new ArrayList<>(myAddedFiles);
    myAddedFiles.clear();

    for (AddedFileInfo addedFileInfo : addedFileInfos) {
      final File ioFile = new File(getIOFile(addedFileInfo.myDir), addedFileInfo.myName);
      VirtualFile addedFile = addedFileInfo.myDir.findChild(addedFileInfo.myName);
      if (addedFile == null) {
        addedFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
      }
      if (addedFile != null) {
        final Status fileStatus = getFileStatus(ioFile);
        if (fileStatus == null || !fileStatus.is(StatusType.STATUS_IGNORED)) {
          boolean isIgnored = ChangeListManager.getInstance(myVcs.getProject()).isIgnoredFile(addedFile);
          if (!isIgnored) {
            addedVFiles.add(addedFile);
            copyFromMap.put(addedFile, addedFileInfo.myCopyFrom);
            if (addedFileInfo.myRecursive) {
              recursiveItems.add(addedFile);
            }
          }
        }
      }
    }
  }

  private void processDeletedFiles() {
    final List<Pair<FilePath, WorkingCopyFormat>> deletedFiles = new ArrayList<>();
    final Collection<FilePath> filesToProcess = new ArrayList<>();
    List<VcsException> exceptions = new ArrayList<>();
    final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(myVcs.getProject());

    try {
      fillDeletedFiles(deletedFiles, filesToProcess);
      if (deletedFiles.isEmpty() && filesToProcess.isEmpty() || myUndoingMove) return;
      final VcsShowConfirmationOption.Value value = myVcs.getDeleteConfirmation().getValue();
      if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
        if (!deletedFiles.isEmpty()) {
          final Collection<FilePath> confirmed = promptAboutDeletion(deletedFiles, value, vcsHelper);
          if (confirmed != null) {
            filesToProcess.addAll(confirmed);
          }
        }
        if (!filesToProcess.isEmpty()) {
          runInBackground("Deleting files from Subversion", createDeleteRunnable(filesToProcess, exceptions));
        }
        List<FilePath> deletedFilesFiles = map(deletedFiles, Functions.pairFirst());
        for (FilePath file : deletedFilesFiles) {
          final FilePath parent = file.getParentPath();
          if (parent != null) {
            myFilesToRefresh.add(parent.getVirtualFile());
          }
        }
        deletedFilesFiles.removeAll(filesToProcess);
        for (FilePath file : deletedFilesFiles) {
          FileUtil.delete(file.getIOFile());
        }
      }
    }
    catch (VcsException e) {
      exceptions.add(e);
    }
    if (!exceptions.isEmpty()) {
      vcsHelper.showErrors(exceptions, SvnBundle.message("delete.files.errors.title"));
    }
  }

  private Runnable createDeleteRunnable(final Collection<? extends FilePath> filesToProcess, final List<? super VcsException> exceptions) {
    return () -> {
      for (FilePath file : filesToProcess) {
        VirtualFile vFile = file.getVirtualFile();  // for deleted directories
        final File ioFile = new File(file.getPath());
        try {
          createDeleteAction(ioFile, true).execute();
          if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
            vFile.refresh(true, true);
            VcsDirtyScopeManager.getInstance(myVcs.getProject()).dirDirtyRecursively(vFile);
          }
          else {
            VcsDirtyScopeManager.getInstance(myVcs.getProject()).fileDirty(file);
          }
        }
        catch (VcsException e) {
          exceptions.add(e);
        }
      }
    };
  }

  private Collection<FilePath> promptAboutDeletion(List<? extends Pair<FilePath, WorkingCopyFormat>> deletedFiles,
                                                   VcsShowConfirmationOption.Value value,
                                                   AbstractVcsHelper vcsHelper) {
    Collection<FilePath> filesToProcess;
    if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      filesToProcess = map(deletedFiles, Functions.pairFirst());
    }
    else {

      final String singleFilePrompt;
      if (deletedFiles.size() == 1 && deletedFiles.get(0).getFirst().isDirectory()) {
        singleFilePrompt = deletedFiles.get(0).getSecond().isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) ?
                           SvnBundle.message("confirmation.text.delete.dir.17") :
                           SvnBundle.message("confirmation.text.delete.dir");
      }
      else {
        singleFilePrompt = SvnBundle.message("confirmation.text.delete.file");
      }
      Collection<FilePath> files = vcsHelper
        .selectFilePathsToProcess(map(deletedFiles, Functions.pairFirst()), SvnBundle.message("confirmation.title.delete.multiple.files"),
                                  null, SvnBundle.message("confirmation.title.delete.file"), singleFilePrompt,
                                  myVcs.getDeleteConfirmation());
      filesToProcess = files == null ? null : new ArrayList<>(files);
    }
    return filesToProcess;
  }

  private void fillDeletedFiles(List<? super Pair<FilePath, WorkingCopyFormat>> deletedFiles, Collection<? super FilePath> deleteAnyway)
    throws VcsException {
    Collection<File> files = new ArrayList<>(myDeletedFiles);
    myDeletedFiles.clear();

    for (final File file : files) {
      final Status status = new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() throws VcsException {
          myT = myVcs.getFactory(file).createStatusClient().doStatus(file, false);
        }
      }.compute();

      final FilePath filePath = VcsUtil.getFilePath(file);
      if (status.is(StatusType.STATUS_ADDED)) {
        deleteAnyway.add(filePath);
      }
      else {
        deletedFiles.add(Pair.create(filePath, myVcs.getWorkingCopyFormat(file)));
      }
    }
  }

  private void processMovedFiles() {
    if (myMovedFiles.isEmpty()) return;

    final Runnable runnable = () -> {
      for (Iterator<MovedFileInfo> iterator = myMovedFiles.iterator(); iterator.hasNext(); ) {
        MovedFileInfo movedFileInfo = iterator.next();
        doMove(movedFileInfo.mySrc, movedFileInfo.myDst);
        iterator.remove();
      }
    };
    runInBackground("Moving files in Subversion", runnable);
  }

  @NotNull
  private static File getIOFile(@NotNull VirtualFile vf) {
    return virtualToIoFile(vf).getAbsoluteFile();
  }

  @Nullable
  private Status getFileStatus(@NotNull final File file) {
    try {
      return new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() throws VcsException {
          myT = myVcs.getFactory(file).createStatusClient().doStatus(file, false);
        }
      }.compute();
    }
    catch (VcsException e) {
      return null;
    }
  }

  private boolean isUndo() {
    return UndoManager.getInstance(myVcs.getProject()).isUndoInProgress();
  }

  private void startOperation(@NotNull VirtualFile file) {
    // currently actions like "new project", "import project" (probably also others) are not performed under command
    if (!myIsInCommand) {
      myIsOperationStarted = myVcs.getProject() == ProjectLocator.getInstance().guessProjectForFile(file);
      if (myIsOperationStarted) commandStarted();
    }
  }

  @Override
  public void afterDone(@NotNull final ThrowableConsumer<LocalFileOperationsHandler, IOException> invoker) {
    if (!myIsInCommand && myIsOperationStarted) commandFinished();
  }
}
