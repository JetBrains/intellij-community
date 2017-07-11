/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.Functions;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.ActionWithTempFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNMoveClient;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.map;

public class SvnFileSystemListener implements LocalFileOperationsHandler, Disposable, CommandListener {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnFileSystemListener");
  private final LocalFileSystem myLfs;

  private static class AddedFileInfo {
    private final VirtualFile myDir;
    private final String myName;
    @Nullable private final File myCopyFrom;
    private final boolean myRecursive;

    public AddedFileInfo(final VirtualFile dir, final String name, @Nullable final File copyFrom, boolean recursive) {
      myDir = dir;
      myName = name;
      myCopyFrom = copyFrom;
      myRecursive = recursive;
    }
  }

  private static class MovedFileInfo {
    private final Project myProject;
    private final File mySrc;
    private final File myDst;

    private MovedFileInfo(final Project project, final File src, final File dst) {
      myProject = project;
      mySrc = src;
      myDst = dst;
    }
  }

  private final MultiMap<Project, AddedFileInfo> myAddedFiles = new MultiMap<>();
  private final MultiMap<Project, File> myDeletedFiles = new MultiMap<>();
  private final List<MovedFileInfo> myMovedFiles = new ArrayList<>();
  private final Map<Project, List<VcsException>> myMoveExceptions = new HashMap<>();
  private final List<VirtualFile> myFilesToRefresh = new ArrayList<>();
  @Nullable private File myStorageForUndo;
  private final List<Couple<File>> myUndoStorageContents = new ArrayList<>();
  private boolean myUndoingMove = false;

  private boolean myIsInCommand;
  @Nullable private Project myGuessedProject;

  public SvnFileSystemListener() {
    myLfs = LocalFileSystem.getInstance();

    myLfs.registerAuxiliaryFileOperationsHandler(this);
    CommandProcessor.getInstance().addCommandListener(this);
  }

  @Override
  public void dispose() {
    myLfs.unregisterAuxiliaryFileOperationsHandler(this);
    CommandProcessor.getInstance().removeCommandListener(this);
  }

  private void addToMoveExceptions(@NotNull final Project project, @NotNull final Exception e) {
    List<VcsException> exceptionList = myMoveExceptions.get(project);
    if (exceptionList == null) {
      exceptionList = new ArrayList<>();
      myMoveExceptions.put(project, exceptionList);
    }
    exceptionList.add(handleMoveException(e));
  }

  private static VcsException handleMoveException(@NotNull Exception e) {
    VcsException vcsException;
    if (e instanceof SVNException && SVNErrorCode.ENTRY_EXISTS.equals(((SVNException)e).getErrorMessage().getErrorCode()) ||
        e instanceof SvnBindException && ((SvnBindException)e).contains(SVNErrorCode.ENTRY_EXISTS)) {
      vcsException = createMoveTargetExistsError(e);
    }
    else if (e instanceof VcsException) {
      vcsException = (VcsException)e;
    }
    else {
      vcsException = new VcsException(e);
    }
    return vcsException;
  }

  private static VcsException createMoveTargetExistsError(@NotNull Exception e) {
    return new VcsException(Arrays.asList("Target of move operation is already under version control.",
                                          "Subversion move had not been performed. ", e.getMessage()));
  }

  @Nullable
  public File copy(final VirtualFile file, final VirtualFile toDir, final String copyName) throws IOException {
    startOperation(file);

    SvnVcs vcs = getVCS(toDir);
    if (vcs == null) {
      vcs = getVCS(file);
    }
    if (vcs == null) {
      return null;
    }

    File srcFile = virtualToIoFile(file);
    File destFile = new File(virtualToIoFile(toDir), copyName);
    if (!SvnUtil.isSvnVersioned(vcs, destFile.getParentFile()) && !isPendingAdd(vcs.getProject(), toDir)) {
      return null;
    }

    if (!SvnUtil.isSvnVersioned(vcs, srcFile.getParentFile())) {
      myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(toDir, copyName, null, false));
      return null;
    }

    final Status fileStatus = getFileStatus(vcs, srcFile);
    if (fileStatus != null && fileStatus.is(StatusType.STATUS_ADDED)) {
      myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(toDir, copyName, null, false));
      return null;
    }

    if (sameRoot(vcs, file.getParent(), toDir)) {
      myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(toDir, copyName, srcFile, false));
      return null;
    }

    myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(toDir, copyName, null, false));
    return null;
  }

  private boolean sameRoot(final SvnVcs vcs, final VirtualFile srcDir, final VirtualFile dstDir) {
    final UUIDHelper helper = new UUIDHelper(vcs);
    final String srcUUID = helper.getRepositoryUUID(vcs.getProject(), srcDir);
    final String dstUUID = helper.getRepositoryUUID(vcs.getProject(), dstDir);

    return srcUUID != null && dstUUID != null && srcUUID.equals(dstUUID);
  }

  private class UUIDHelper {
    private final SvnVcs myVcs;

    private UUIDHelper(final SvnVcs vcs) {
      myVcs = vcs;
    }

    /**
     * passed dir must be under VC control (it is assumed)
     */
    @Nullable
    public String getRepositoryUUID(final Project project, final VirtualFile dir) {
      try {
        final Info info1 = new RepeatSvnActionThroughBusy() {
          @Override
          protected void executeImpl() {
            myT = myVcs.getInfo(virtualToIoFile(dir));
          }
        }.compute();
        if (info1 == null || info1.getRepositoryUUID() == null) {
          // go deeper if current parent was added (if parent was added, it theoretically could NOT know its repo UUID)
          final VirtualFile parent = dir.getParent();
          if (parent == null) {
            return null;
          }
          if (isPendingAdd(project, parent)) {
            return getRepositoryUUID(project, parent);
          }
        } else {
          return info1.getRepositoryUUID();
        }
      }
      catch (VcsException e) {
        // go to return default
      }
      return null;
    }
  }

  public boolean move(VirtualFile file, VirtualFile toDir) throws IOException {
    startOperation(file);

    File srcFile = getIOFile(file);
    File dstFile = new File(getIOFile(toDir), file.getName());

    final SvnVcs vcs = getVCS(toDir);
    final SvnVcs sourceVcs = getVCS(file);
    if (vcs == null) {
      return false;
    }

    // save all documents here when !myMovedFiles.isEmpty() deletes these files from VFS
    // these leads to psi invalidation during refactoring inside write action
    // FileDocumentManager.getInstance().saveAllDocuments();
    if (sourceVcs == null) {
      return createItem(toDir, file.getName(), file.isDirectory(), true);
    }

    if (isPendingAdd(vcs.getProject(), toDir)) {
      myMovedFiles.add(new MovedFileInfo(sourceVcs.getProject(), srcFile, dstFile));
      return true; 
    }
    else {
      myFilesToRefresh.add(file.getParent());
      myFilesToRefresh.add(toDir);
      return doMove(sourceVcs, srcFile, dstFile);
    }
  }

  public boolean rename(VirtualFile file, String newName) throws IOException {
    startOperation(file);

    File srcFile = getIOFile(file);
    File dstFile = new File(srcFile.getParentFile(), newName);
    SvnVcs vcs = getVCS(file);
    if (vcs != null) {
      // save all documents here when !myMovedFiles.isEmpty() deletes these files from VFS
      // these leads to psi invalidation during refactoring inside write action
      // FileDocumentManager.getInstance().saveAllDocuments();

      myFilesToRefresh.add(file.getParent());
      return doMove(vcs, srcFile, dstFile);
    }
    return false;
  }

  private boolean doMove(@NotNull SvnVcs vcs, final File src, final File dst) {
    try {
      final boolean isUndo = isUndo(vcs);
      final String list = isUndo ? null : SvnChangelistListener.getCurrentMapping(vcs, src);

      WorkingCopyFormat format = vcs.getWorkingCopyFormat(src);
      final boolean is17OrLater = format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN);
      if (is17OrLater) {
        Status srcStatus = getFileStatus(vcs, src);
        if (isUnversioned(srcStatus) && (isUnversioned(vcs, dst.getParentFile()) || isUnversioned(vcs, dst)) ||
            for17move(vcs, src, dst, isUndo, srcStatus)) {
          return false;
        }
      } else {
        if (for16move(vcs, src, dst, isUndo)) return false;
      }

      if (! isUndo && list != null) {
        SvnChangelistListener.putUnderList(vcs, list, dst);
      }
    }
    catch(VcsException e) {
      addToMoveExceptions(vcs.getProject(), e);
      return false;
    }
    return true;
  }

  private static boolean isUnversioned(@Nullable Status status) {
    return status == null || status.is(StatusType.STATUS_UNVERSIONED);
  }

  private static boolean isUnversioned(@NotNull SvnVcs vcs, @NotNull File file) {
    return isUnversioned(getFileStatus(vcs, file));
  }

  private boolean for17move(final SvnVcs vcs, final File src, final File dst, boolean undo, Status srcStatus) throws VcsException {
    if (srcStatus != null && srcStatus.getCopyFromURL() == null) {
      undo = false;
    }
    if (undo) {
      myUndoingMove = true;
      boolean isCaseOnlyMove = FileUtil.filesEqual(src, dst);
      createRevertAction(vcs, isCaseOnlyMove ? src : dst, true).execute();
      copyUnversionedMembersOfDirectory(src, dst);
      if (isUnversioned(srcStatus)) {
        FileUtil.delete(src);
      } else {
        createRevertAction(vcs, isCaseOnlyMove ? dst : src, true).execute();
      }
      restoreFromUndoStorage(dst);
    } else {
      if (doUsualMove(vcs, src)) return true;
      // check destination directory
      if (isUnversioned(vcs, dst.getParentFile())) {
        try {
          FileUtil.copyFileOrDir(src, dst);
        }
        catch (IOException e) {
          throw new SvnBindException(e);
        }
        createDeleteAction(vcs, src, true).execute();
        return false;
      }
      moveFileWithSvn(vcs, src, dst);
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

  private static boolean doUsualMove(SvnVcs vcs, File src) {
    // if src is not under version control, do usual move.
    Status srcStatus = getFileStatus(vcs, src);
    return srcStatus == null ||
           srcStatus.is(StatusType.STATUS_UNVERSIONED, StatusType.STATUS_OBSTRUCTED, StatusType.STATUS_MISSING, StatusType.STATUS_EXTERNAL);
  }

  private boolean for16move(SvnVcs vcs, final File src, final File dst, final boolean undo) throws VcsException {
    final SVNMoveClient mover = vcs.getSvnKitManager().createMoveClient();
    if (undo) {
      myUndoingMove = true;
      restoreFromUndoStorage(dst);
    }
    else if (doUsualMove(vcs, src)) return true;

    new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws VcsException {
        try {
          if (undo) {
            mover.undoMove(src, dst);
          }
          else {
            mover.doMove(src, dst);
          }
        }
        catch (SVNException e) {
          throw new SvnBindException(e);
        }
      }
    }.execute();

    return false;
  }

  private void restoreFromUndoStorage(final File dst) {
    String normPath = FileUtil.toSystemIndependentName(dst.getPath());
    for (Iterator<Couple<File>> it = myUndoStorageContents.iterator(); it.hasNext();) {
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


  public boolean createFile(VirtualFile dir, String name) throws IOException {
    startOperation(dir);

    return createItem(dir, name, false, false);
  }

  public boolean createDirectory(VirtualFile dir, String name) throws IOException {
    startOperation(dir);

    return createItem(dir, name, true, false);
  }

  /**
   * delete file or directory (both 'undo' and 'do' modes)
   * unversioned: do nothing, return false
   * obstructed: do nothing, return false
   * external or wc root: do nothing, return false
   * missing: do nothing, return false
   * <p/>
   * versioned: schedule for deletion, return true
   * added: schedule for deletion (make unversioned), return true
   * copied, but not scheduled: schedule for deletion, return true
   * replaced: schedule for deletion, return true
   * <p/>
   * deleted: do nothing, return true (strange)
   */
  public boolean delete(VirtualFile file) throws IOException {
    startOperation(file);

    final SvnVcs vcs = getVCS(file);
    if (vcs != null && SvnUtil.isAdminDirectory(file)) {
      return true;
    }
    if (vcs == null) return false;
    final VcsShowConfirmationOption.Value value = vcs.getDeleteConfirmation().getValue();
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(value)) return false;

    final File ioFile = getIOFile(file);
    if (!SvnUtil.isSvnVersioned(vcs, ioFile.getParentFile()) || SvnUtil.isWorkingCopyRoot(ioFile)) {
      return false;
    }

    Status status = getFileStatus(vcs, ioFile);

    if (status == null ||
        status.is(StatusType.STATUS_UNVERSIONED, StatusType.STATUS_OBSTRUCTED, StatusType.STATUS_MISSING, StatusType.STATUS_EXTERNAL,
                  StatusType.STATUS_IGNORED)) {
      return false;
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      if (isUndo(vcs)) {
        moveToUndoStorage(file);
      }
      return true;
    }
    else {
        if (isAboveSourceOfCopyOrMove(vcs.getProject(), ioFile)) {
          myDeletedFiles.putValue(vcs.getProject(), ioFile);
          return true;
        }
        if (status.is(StatusType.STATUS_ADDED)) {
          try {
            createRevertAction(vcs, ioFile, false).execute();
          }
          catch (VcsException e) {
            // ignore
          }
        }
        else {
          myDeletedFiles.putValue(vcs.getProject(), ioFile);
          // packages deleted from disk should not be deleted from svn (IDEADEV-16066)
          if (file.isDirectory() || isUndo(vcs)) return true;
        }
      return false;
    }
  }

  @NotNull
  private static RepeatSvnActionThroughBusy createRevertAction(@NotNull final SvnVcs vcs,
                                                               @NotNull final File file,
                                                               final boolean recursive) {
    return new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws VcsException {
        vcs.getFactory(file).createRevertClient().revert(Collections.singletonList(file), Depth.allOrFiles(recursive), null);
      }
    };
  }

  @NotNull
  private static RepeatSvnActionThroughBusy createDeleteAction(@NotNull final SvnVcs vcs, @NotNull final File file, final boolean force) {
    return new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws VcsException {
        vcs.getFactory(file).createDeleteClient().delete(file, force, false, null);
      }
    };
  }

  private boolean isAboveSourceOfCopyOrMove(final Project p, File ioFile) {
    for (MovedFileInfo file : myMovedFiles) {
      if (FileUtil.isAncestor(ioFile, file.mySrc, false)) return true;
    }
    for (AddedFileInfo info : myAddedFiles.get(p)) {
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
    SvnVcs vcs = getVCS(dir);
    if (vcs == null) {
      return false;
    }
    final VcsShowConfirmationOption.Value value = vcs.getAddConfirmation().getValue();
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(value)) return false;

    if (isUndo(vcs) && SvnUtil.isAdminDirectory(dir, name)) {
      return false;      
    }
    File ioDir = getIOFile(dir);
    boolean pendingAdd = isPendingAdd(vcs.getProject(), dir);
    if (!SvnUtil.isSvnVersioned(vcs, ioDir) && !pendingAdd) {
      return false;
    }
    final File targetFile = new File(ioDir, name);
    Status status = getFileStatus(vcs, targetFile);

    if (status == null || status.getContentsStatus() == StatusType.STATUS_NONE ||
        status.getContentsStatus() == StatusType.STATUS_UNVERSIONED) {
      myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(dir, name, null, recursive));
      return false;
    }
    else if (status.is(StatusType.STATUS_MISSING)) {
      return false;
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      NodeKind kind = status.getKind();
      // kind differs.
      if (directory && !kind.isDirectory() || !directory && !kind.isFile()) {
        return false;
      }
      try {
        if (isUndo(vcs)) {
          createRevertAction(vcs, targetFile, false).execute();
          return true;
        }
        myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(dir, name, null, recursive));
        return false;
      }
      catch (VcsException e) {
        SVNFileUtil.deleteAll(targetFile, true);
        return false;
      }
    }
    return false;
  }

  private boolean isPendingAdd(final Project project, final VirtualFile dir) {
    final Collection<AddedFileInfo> addedFileInfos = myAddedFiles.get(project);
    for(AddedFileInfo i: addedFileInfos) {
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
    final Project project = event.getProject();
    if (project == null) return;
    commandStarted(project);
  }

  void commandStarted(@NotNull Project project) {
    myUndoingMove = false;
    myMoveExceptions.remove(project);
  }

  @Override
  public void commandFinished(@NotNull CommandEvent event) {
    myIsInCommand = false;
    final Project project = event.getProject();
    if (project == null) return;
    commandFinished(project);
  }

  void commandFinished(@NotNull Project project) {
    checkOverwrites(project);
    if (myAddedFiles.containsKey(project)) {
      processAddedFiles(project);
    }
    processMovedFiles(project);
    if (myDeletedFiles.containsKey(project)) {
      processDeletedFiles(project);
    }

    final List<VcsException> exceptionList = myMoveExceptions.get(project);
    if (exceptionList != null && ! exceptionList.isEmpty()) {
      AbstractVcsHelper.getInstance(project).showErrors(exceptionList, SvnBundle.message("move.files.errors.title"));
    }

    if (!myFilesToRefresh.isEmpty()) {
      refreshFiles(project);
    }
  }

  private void checkOverwrites(final Project project) {
    final Collection<AddedFileInfo> addedFileInfos = myAddedFiles.get(project);
    final Collection<File> deletedFiles = myDeletedFiles.get(project);
    if (addedFileInfos.isEmpty() || deletedFiles.isEmpty()) return;
    final Iterator<AddedFileInfo> iterator = addedFileInfos.iterator();
    while (iterator.hasNext()) {
      AddedFileInfo addedFileInfo = iterator.next();
      final File ioFile = new File(addedFileInfo.myDir.getPath(), addedFileInfo.myName);
      if (deletedFiles.remove(ioFile)) {
        iterator.remove();
      }
    }
  }

  private void refreshFiles(final Project project) {
    final List<VirtualFile> toRefreshFiles = new ArrayList<>();
    final List<VirtualFile> toRefreshDirs = new ArrayList<>();
    for (VirtualFile file : myFilesToRefresh) {
      if (file == null) continue;
      if (file.isDirectory()) {
        toRefreshDirs.add(file);
      } else {
        toRefreshFiles.add(file);
      }
    }
    // if refresh asynchronously, local changes would also be notified that they are dirty asynchronously,
    // and commit could be executed while not all changes are visible
    filterOutInvalid(myFilesToRefresh);
    RefreshQueue.getInstance().refresh(true, true, () -> {
      if (project.isDisposed()) return;
      filterOutInvalid(toRefreshFiles);
      filterOutInvalid(toRefreshDirs);

      final VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
      vcsDirtyScopeManager.filesDirty(toRefreshFiles, toRefreshDirs);
    }, myFilesToRefresh);
    myFilesToRefresh.clear();
  }

  private static void filterOutInvalid(@NotNull Collection<VirtualFile> files) {
    for (Iterator<VirtualFile> iterator = files.iterator(); iterator.hasNext();) {
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

  private void processAddedFiles(final Project project) {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final List<VirtualFile> addedVFiles = new ArrayList<>();
    final Map<VirtualFile, File> copyFromMap = new HashMap<>();
    final Set<VirtualFile> recursiveItems = new HashSet<>();
    fillAddedFiles(project, vcs, addedVFiles, copyFromMap, recursiveItems);
    if (addedVFiles.isEmpty()) return;
    final VcsShowConfirmationOption.Value value = vcs.getAddConfirmation().getValue();
    if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      // Current method could be invoked under write action (for instance, during project import). So we explicitly use
      // Application.invokeLater() in such cases to prevent deadlocks (while accessing vcs root mappings) and also not to show dialog under
      // write action.
      runNotUnderWriteAction(project, () -> {
        final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
        final Collection<VirtualFile> filesToProcess = promptAboutAddition(vcs, addedVFiles, value, vcsHelper);
        if (filesToProcess != null && !filesToProcess.isEmpty()) {
          final List<VcsException> exceptions = new ArrayList<>();
          runInBackground(project, "Adding files to Subversion",
                          createAdditionRunnable(project, vcs, copyFromMap, filesToProcess, exceptions));
          if (!exceptions.isEmpty()) {
            vcsHelper.showErrors(exceptions, SvnBundle.message("add.files.errors.title"));
          }
        }
      });
    }
  }

  private static void runNotUnderWriteAction(@NotNull Project project, @NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed()) {
      application.invokeLater(runnable, project.getDisposed());
    }
    else {
      runnable.run();
    }
  }

  private static void runInBackground(final Project project, final String name, final Runnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, name, false, project);
    } else {
      runnable.run();
    }
  }

  private static Runnable createAdditionRunnable(final Project project,
                               final SvnVcs vcs,
                               final Map<VirtualFile, File> copyFromMap,
                               final Collection<VirtualFile> filesToProcess,
                               final List<VcsException> exceptions) {
    return () -> {
      for (VirtualFile file : filesToProcess) {
        final File ioFile = virtualToIoFile(file);
        try {
          final File copyFrom = copyFromMap.get(file);
          if (copyFrom != null) {
            try {
              new ActionWithTempFile(ioFile) {
                protected void executeInternal() throws VcsException {
                  // not recursive
                  new RepeatSvnActionThroughBusy() {
                    @Override
                    protected void executeImpl() throws VcsException {
                      vcs.getFactory(copyFrom).createCopyMoveClient().copy(copyFrom, ioFile, true, false);
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
                vcs.getFactory(ioFile).createAddClient().add(ioFile, null, false, false, true, null);
              }
            }.execute();
          }
          VcsDirtyScopeManager.getInstance(project).fileDirty(file);
        }
        catch (VcsException e) {
          exceptions.add(e);
        }
      }
    };
  }

  private static Collection<VirtualFile> promptAboutAddition(SvnVcs vcs,
                                                      List<VirtualFile> addedVFiles,
                                                      VcsShowConfirmationOption.Value value,
                                                      AbstractVcsHelper vcsHelper) {
    Collection<VirtualFile> filesToProcess;
    if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      filesToProcess = addedVFiles;
    }
    else {
      final String singleFilePrompt;
      if (addedVFiles.size() == 1 && addedVFiles.get(0).isDirectory()) {
        singleFilePrompt = SvnBundle.getString("confirmation.text.add.dir");
      }
      else {
        singleFilePrompt = SvnBundle.getString("confirmation.text.add.file");
      }
      filesToProcess = vcsHelper.selectFilesToProcess(addedVFiles, SvnBundle.message("confirmation.title.add.multiple.files"),
                                                      null,
                                                      SvnBundle.message("confirmation.title.add.file"), singleFilePrompt,
                                                      vcs.getAddConfirmation());
    }
    return filesToProcess;
  }

  private void fillAddedFiles(Project project,
                              SvnVcs vcs,
                              List<VirtualFile> addedVFiles,
                              Map<VirtualFile, File> copyFromMap,
                              Set<VirtualFile> recursiveItems) {
    final Collection<AddedFileInfo> addedFileInfos = myAddedFiles.remove(project);
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    for (AddedFileInfo addedFileInfo : addedFileInfos) {
      final File ioFile = new File(getIOFile(addedFileInfo.myDir), addedFileInfo.myName);
      VirtualFile addedFile = addedFileInfo.myDir.findChild(addedFileInfo.myName);
      if (addedFile == null) {
        addedFile = myLfs.refreshAndFindFileByIoFile(ioFile);
      }
      if (addedFile != null) {
        final Status fileStatus = getFileStatus(vcs, ioFile);
        if (fileStatus == null || !fileStatus.is(StatusType.STATUS_IGNORED)) {
          boolean isIgnored = changeListManager.isIgnoredFile(addedFile);
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

  private void processDeletedFiles(Project project) {
    final List<Pair<FilePath, WorkingCopyFormat>> deletedFiles = new ArrayList<>();
    final Collection<FilePath> filesToProcess = new ArrayList<>();
    List<VcsException> exceptions = new ArrayList<>();
    final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);

    try {
      fillDeletedFiles(project, deletedFiles, filesToProcess);
      if (deletedFiles.isEmpty() && filesToProcess.isEmpty() || myUndoingMove) return;
      SvnVcs vcs = SvnVcs.getInstance(project);
      final VcsShowConfirmationOption.Value value = vcs.getDeleteConfirmation().getValue();
      if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
        if (! deletedFiles.isEmpty()) {
          final Collection<FilePath> confirmed = promptAboutDeletion(deletedFiles, vcs, value, vcsHelper);
          if (confirmed != null) {
            filesToProcess.addAll(confirmed);
          }
        }
        if (!filesToProcess.isEmpty()) {
          runInBackground(project, "Deleting files from Subversion", createDeleteRunnable(project, vcs, filesToProcess, exceptions));
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
    } catch (VcsException e) {
      exceptions.add(e);
    }
    if (! exceptions.isEmpty()) {
      vcsHelper.showErrors(exceptions, SvnBundle.message("delete.files.errors.title"));
    }
  }

  private static Runnable createDeleteRunnable(final Project project,
                                        final SvnVcs vcs,
                                        final Collection<FilePath> filesToProcess,
                                        final List<VcsException> exceptions) {
    return () -> {
      for (FilePath file : filesToProcess) {
        VirtualFile vFile = file.getVirtualFile();  // for deleted directories
        final File ioFile = new File(file.getPath());
        try {
          createDeleteAction(vcs, ioFile, true).execute();
          if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
            vFile.refresh(true, true);
            VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(vFile);
          }
          else {
            VcsDirtyScopeManager.getInstance(project).fileDirty(file);
          }
        }
        catch (VcsException e) {
          exceptions.add(e);
        }
      }
    };
  }

  private static Collection<FilePath> promptAboutDeletion(List<Pair<FilePath, WorkingCopyFormat>> deletedFiles,
                                                   SvnVcs vcs,
                                                   VcsShowConfirmationOption.Value value,
                                                   AbstractVcsHelper vcsHelper) {
    Collection<FilePath> filesToProcess;
    if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      filesToProcess = map(deletedFiles, Functions.pairFirst());
    } else {

      final String singleFilePrompt;
      if (deletedFiles.size() == 1 && deletedFiles.get(0).getFirst().isDirectory()) {
        singleFilePrompt = deletedFiles.get(0).getSecond().isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) ?
                           SvnBundle.getString("confirmation.text.delete.dir.17") :
                           SvnBundle.getString("confirmation.text.delete.dir");
      }
      else {
        singleFilePrompt = SvnBundle.getString("confirmation.text.delete.file");
      }
      Collection<FilePath> files = vcsHelper
        .selectFilePathsToProcess(map(deletedFiles, Functions.pairFirst()), SvnBundle.message("confirmation.title.delete.multiple.files"),
                                  null, SvnBundle.message("confirmation.title.delete.file"), singleFilePrompt, vcs.getDeleteConfirmation());
      filesToProcess = files == null ? null : new ArrayList<>(files);
    }
    return filesToProcess;
  }

  private void fillDeletedFiles(Project project, List<Pair<FilePath, WorkingCopyFormat>> deletedFiles, Collection<FilePath> deleteAnyway)
    throws VcsException {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final Collection<File> files = myDeletedFiles.remove(project);
    for (final File file : files) {
      final Status status = new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() throws VcsException {
          myT = vcs.getFactory(file).createStatusClient().doStatus(file, false);
        }
      }.compute();

      final FilePath filePath = VcsUtil.getFilePath(file);
      if (StatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
        deleteAnyway.add(filePath);
      } else {
        deletedFiles.add(Pair.create(filePath, vcs.getWorkingCopyFormat(file)));
      }
    }
  }

  private void processMovedFiles(final Project project) {
    if (myMovedFiles.isEmpty()) return;

    final Runnable runnable = () -> {
      for (Iterator<MovedFileInfo> iterator = myMovedFiles.iterator(); iterator.hasNext(); ) {
        MovedFileInfo movedFileInfo = iterator.next();
        if (movedFileInfo.myProject == project) {
          doMove(SvnVcs.getInstance(project), movedFileInfo.mySrc, movedFileInfo.myDst);
          iterator.remove();
        }
      }
    };
    runInBackground(project, "Moving files in Subversion", runnable);
  }

  @Nullable
  private static SvnVcs getVCS(VirtualFile file) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      if (vcs instanceof SvnVcs) {
        return (SvnVcs)vcs;
      }
    }
    return null;
  }


  private static File getIOFile(VirtualFile vf) {
    return virtualToIoFile(vf).getAbsoluteFile();
  }

  @Nullable
  private static Status getFileStatus(@NotNull final SvnVcs vcs, @NotNull final File file) {
    try {
      return new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() throws VcsException {
          myT = vcs.getFactory(file).createStatusClient().doStatus(file, false);
        }
      }.compute();
    }
    catch (VcsException e) {
      return null;
    }
  }

  private static boolean isUndo(SvnVcs vcs) {
    if (vcs == null || vcs.getProject() == null) {
      return false;
    }
    Project p = vcs.getProject();
    return UndoManager.getInstance(p).isUndoInProgress();
  }

  public void startOperation(@NotNull VirtualFile file) {
    if (!myIsInCommand) {
      // currently actions like "new project", "import project" (probably also others) are not performed under command
      myGuessedProject = ProjectLocator.getInstance().guessProjectForFile(file);
      if (myGuessedProject != null) {
        commandStarted(myGuessedProject);
      }
    }
  }

  public void afterDone(final ThrowableConsumer<LocalFileOperationsHandler, IOException> invoker) {
    if (!myIsInCommand && myGuessedProject != null) {
      commandFinished(myGuessedProject);
      myGuessedProject = null;
    }
  }
}
