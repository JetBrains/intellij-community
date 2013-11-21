/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.ActionWithTempFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SvnFileSystemListener extends CommandAdapter implements LocalFileOperationsHandler {
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

  private final MultiMap<Project, AddedFileInfo> myAddedFiles = new MultiMap<Project, AddedFileInfo>();
  private final MultiMap<Project, File> myDeletedFiles = new MultiMap<Project, File>();
  private final List<MovedFileInfo> myMovedFiles = new ArrayList<MovedFileInfo>();
  private final Map<Project, List<VcsException>> myMoveExceptions = new HashMap<Project, List<VcsException>>();
  private final List<VirtualFile> myFilesToRefresh = new ArrayList<VirtualFile>();
  @Nullable private File myStorageForUndo;
  private final List<Pair<File, File>> myUndoStorageContents = new ArrayList<Pair<File, File>>();
  private boolean myUndoingMove = false;

  public SvnFileSystemListener() {
    myLfs = LocalFileSystem.getInstance();
  }

  private void addToMoveExceptions(@NotNull final Project project, @NotNull final Exception e) {
    List<VcsException> exceptionList = myMoveExceptions.get(project);
    if (exceptionList == null) {
      exceptionList = new ArrayList<VcsException>();
      myMoveExceptions.put(project, exceptionList);
    }
    exceptionList.add(handleMoveException(e));
  }

  private VcsException handleMoveException(@NotNull Exception e) {
    VcsException vcsException;
    if (e instanceof SVNException && SVNErrorCode.ENTRY_EXISTS.equals(((SVNException)e).getErrorMessage().getErrorCode())) {
      vcsException = createMoveTargetExistsError(e);
    }
    else if (e instanceof SvnBindException && ((SvnBindException)e).contains(SVNErrorCode.ENTRY_EXISTS)) {
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
    SvnVcs vcs = getVCS(toDir);
    if (vcs == null) {
      vcs = getVCS(file);
    }
    if (vcs == null) {
      return null;
    }

    File srcFile = new File(file.getPath());
    File destFile = new File(new File(toDir.getPath()), copyName);
    final boolean dstDirUnderControl = SvnUtil.isSvnVersioned(vcs.getProject(), destFile.getParentFile());
    if (! dstDirUnderControl && !isPendingAdd(vcs.getProject(), toDir)) {
      return null;
    }

    if (! SvnUtil.isSvnVersioned(vcs.getProject(), srcFile.getParentFile())) {
      myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(toDir, copyName, null, false));
      return null;
    }

    final SVNStatus fileStatus = getFileStatus(vcs, srcFile);
    if (fileStatus != null && SvnVcs.svnStatusIs(fileStatus, SVNStatusType.STATUS_ADDED)) {
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
        final SVNInfo info1 = new RepeatSvnActionThroughBusy() {
          @Override
          protected void executeImpl() throws SVNException {
            myT = myVcs.getInfo(new File(dir.getPath()));
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
      } catch (SVNException e) {
        // go to return default
      }
      return null;
    }
  }

  public boolean move(VirtualFile file, VirtualFile toDir) throws IOException {
    FileDocumentManager.getInstance().saveAllDocuments();

    File srcFile = getIOFile(file);
    File dstFile = new File(getIOFile(toDir), file.getName());

    final SvnVcs vcs = getVCS(toDir);
    final SvnVcs sourceVcs = getVCS(file);
    if (vcs == null && sourceVcs == null) return false;

    if (vcs == null) {
      return false;
    }
    if (sourceVcs == null) {
      return createItem(toDir, file.getName(), file.isDirectory(), true);
    }

    if (isPendingAdd(vcs.getProject(), toDir)) {

      myMovedFiles.add(new MovedFileInfo(sourceVcs.getProject(), srcFile, dstFile));
      return true; 
    }
    else {
      final VirtualFile oldParent = file.getParent();
      myFilesToRefresh.add(oldParent);
      myFilesToRefresh.add(toDir);
      return doMove(sourceVcs, srcFile, dstFile);
    }
  }

  public boolean rename(VirtualFile file, String newName) throws IOException {
    FileDocumentManager.getInstance().saveAllDocuments();

    File srcFile = getIOFile(file);
    File dstFile = new File(srcFile.getParentFile(), newName);
    SvnVcs vcs = getVCS(file);
    if (vcs != null) {
      myFilesToRefresh.add(file.getParent());
      return doMove(vcs, srcFile, dstFile);
    }
    return false;
  }

  private boolean doMove(@NotNull SvnVcs vcs, final File src, final File dst) {
    long srcTime = src.lastModified();
    try {
      final boolean isUndo = isUndo(vcs);
      final String list = isUndo ? null : SvnChangelistListener.getCurrentMapping(vcs, src);

      WorkingCopyFormat format = vcs.getWorkingCopyFormat(src);
      final boolean is17OrLater = WorkingCopyFormat.ONE_DOT_EIGHT.equals(format) || WorkingCopyFormat.ONE_DOT_SEVEN.equals(format);
      if (is17OrLater) {
        SVNStatus srcStatus = getFileStatus(vcs, src);
        final File toDir = dst.getParentFile();
        SVNStatus dstStatus = getFileStatus(vcs, toDir);
        final boolean srcUnversioned = srcStatus == null || SvnVcs.svnStatusIsUnversioned(srcStatus);
        if (srcUnversioned && (dstStatus == null || SvnVcs.svnStatusIsUnversioned(dstStatus))) {
          return false;
        }
        if (srcUnversioned) {
          SVNStatus dstWasStatus = getFileStatus(vcs, dst);
          if (dstWasStatus == null || SvnVcs.svnStatusIsUnversioned(dstWasStatus)) {
            return false;
          }
        }
        if (for17move(vcs, src, dst, isUndo, srcStatus)) return false;
      } else {
        if (for16move(vcs, src, dst, isUndo)) return false;
      }

      if (! isUndo && list != null) {
        SvnChangelistListener.putUnderList(vcs.getProject(), list, dst);
      }
      dst.setLastModified(srcTime);
    }
    catch (SVNException e) {
      addToMoveExceptions(vcs.getProject(), e);
      return false;
    }
    catch(VcsException e) {
      addToMoveExceptions(vcs.getProject(), e);
      return false;
    }
    return true;
  }

  private final static Set<SVNStatusType> ourStatusesForUndoMove = new HashSet<SVNStatusType>();
  static {
    ourStatusesForUndoMove.add(SVNStatusType.STATUS_ADDED);
  }

  private boolean for17move(final SvnVcs vcs, final File src, final File dst, boolean undo, SVNStatus srcStatus) throws SVNException {
    if (srcStatus != null && srcStatus.getCopyFromURL() == null) {
      undo = false;
    }
    if (undo) {
      myUndoingMove = true;
      createRevertAction(vcs, dst, true).execute();
      copyUnversionedMembersOfDirectory(src, dst);
      if (srcStatus == null || SvnVcs.svnStatusIsUnversioned(srcStatus)) {
        FileUtil.delete(src);
      } else {
        createRevertAction(vcs, src, true).execute();
      }
      restoreFromUndoStorage(dst);
    } else {
      if (doUsualMove(vcs, src)) return true;
      // check destination directory
      final SVNStatus dstParentStatus = getFileStatus(vcs, dst.getParentFile());
      if (dstParentStatus == null || SvnVcs.svnStatusIsUnversioned(dstParentStatus)) {
        try {
          copyFileOrDir(src, dst);
        }
        catch (IOException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
        }
        createDeleteAction(vcs, src, true).execute();
        return false;
      }
      moveFileWithSvn(vcs, src, dst);
    }
    return false;
  }

  public static void moveFileWithSvn(final SvnVcs vcs, final File src, final File dst) throws SVNException {
    new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws SVNException {
        try {
          vcs.getFactory(src).createCopyMoveClient().copy(src, dst, false, true);
        }
        catch (VcsException e) {
          wrapAndThrow(e);
        }
      }
    }.execute();
  }

  private void copyUnversionedMembersOfDirectory(final File src, final File dst) throws SVNException {
    if (src.isDirectory()) {
      final SVNException[] exc = new SVNException[1];
      FileUtil.processFilesRecursively(src, new Processor<File>() {
        @Override
        public boolean process(File file) {
          String relativePath = FileUtil.getRelativePath(src, file);
          File newFile = new File(dst, relativePath);
          if (!newFile.exists()) {
            try {
              copyFileOrDir(src, dst);
            }
            catch (IOException e) {
              exc[0] = new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
              return false;
            }
          }
          return true;
        }
      });
      if (exc[0] != null) {
        throw exc[0];
      }
    }
  }

  private void copyFileOrDir(File src, File dst) throws IOException {
    if (src.isDirectory()) {
      FileUtil.copyDir(src, dst);
    } else {
      FileUtil.copy(src, dst);
    }
  }

  private boolean doUsualMove(SvnVcs vcs, File src) {
    // if src is not under version control, do usual move.
    SVNStatus srcStatus = getFileStatus(vcs, src);
    if (srcStatus == null || SvnVcs.svnStatusIsUnversioned(srcStatus) ||
        SvnVcs.svnStatusIs(srcStatus, SVNStatusType.STATUS_OBSTRUCTED) ||
        SvnVcs.svnStatusIs(srcStatus, SVNStatusType.STATUS_MISSING) ||
        SvnVcs.svnStatusIs(srcStatus, SVNStatusType.STATUS_EXTERNAL)) {
      return true;
    }
    return false;
  }

  private boolean for16move(SvnVcs vcs, final File src, final File dst, boolean undo) throws SVNException {
    final SVNMoveClient mover = vcs.createMoveClient();
    if (undo) {
      myUndoingMove = true;
      restoreFromUndoStorage(dst);
      new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() throws SVNException {
          mover.undoMove(src, dst);
        }
      }.execute();
    }
    else {
      // if src is not under version control, do usual move.
      if (doUsualMove(vcs, src)) return true;
      new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() throws SVNException {
          mover.doMove(src, dst);
        }
      }.execute();
    }
    return false;
  }

  private void restoreFromUndoStorage(final File dst) {
    String normPath = FileUtil.toSystemIndependentName(dst.getPath());
    for (Iterator<Pair<File, File>> it = myUndoStorageContents.iterator(); it.hasNext();) {
      Pair<File, File> e = it.next();
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
    return createItem(dir, name, false, false);
  }

  public boolean createDirectory(VirtualFile dir, String name) throws IOException {
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
    final SvnVcs vcs = getVCS(file);
    if (vcs != null && SvnUtil.isAdminDirectory(file)) {
      return true;
    }
    if (vcs == null) return false;
    final VcsShowConfirmationOption.Value value = vcs.getDeleteConfirmation().getValue();
    if (VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY.equals(value)) return false;

    final File ioFile = getIOFile(file);
    if (! SvnUtil.isSvnVersioned(vcs.getProject(), ioFile.getParentFile())) {
      return false;
    }
    if (SvnUtil.isWorkingCopyRoot(ioFile)) {
      return false;
    }

    SVNStatus status = getFileStatus(vcs, ioFile);

    if (status == null ||
        SvnVcs.svnStatusIsUnversioned(status) ||
        SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_OBSTRUCTED) ||
        SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_MISSING) ||
        SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_EXTERNAL) ||
        SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_IGNORED)) {
      return false;
    } else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_DELETED)) {
      if (isUndo(vcs)) {
        moveToUndoStorage(file);
      }
      return true;
    }
    else {
      if (vcs != null) {
        if (isAboveSourceOfCopyOrMove(vcs.getProject(), ioFile)) {
          myDeletedFiles.putValue(vcs.getProject(), ioFile);
          return true;
        }
        if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_ADDED)) {
          try {
            createRevertAction(vcs, ioFile, false).execute();
          }
          catch (SVNException e) {
            // ignore
          }
        }
        else {
          myDeletedFiles.putValue(vcs.getProject(), ioFile);
          // packages deleted from disk should not be deleted from svn (IDEADEV-16066)
          if (file.isDirectory() || isUndo(vcs)) return true;
        }
      }
      return false;
    }
  }

  @NotNull
  private RepeatSvnActionThroughBusy createRevertAction(@NotNull final SvnVcs vcs, @NotNull final File file, final boolean recursive) {
    return new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws SVNException {
        try {
          vcs.getFactory(file).createRevertClient().revert(new File[]{file}, SVNDepth.fromRecurse(recursive), null);
        }
        catch (VcsException e) {
          wrapAndThrow(e);
        }
      }
    };
  }

  @NotNull
  private RepeatSvnActionThroughBusy createDeleteAction(@NotNull final SvnVcs vcs, @NotNull final File file, final boolean force) {
    return new RepeatSvnActionThroughBusy() {
      @Override
      protected void executeImpl() throws SVNException {
        try {
          vcs.getFactory(file).createDeleteClient().delete(file, force, false, null);
        }
        catch (VcsException e) {
          wrapAndThrow(e);
        }
      }
    };
  }

  private static void wrapAndThrow(VcsException e) throws SVNException {
    // TODO: probably we should wrap into new exception only if e.getCause is not SVNException
    throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, e), e);
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
    myUndoStorageContents.add(0, new Pair<File, File>(new File(file.getPath()), tmpFile));
    new File(file.getPath()).renameTo(tmpFile);
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
    if (! SvnUtil.isSvnVersioned(vcs.getProject(), ioDir) && ! pendingAdd) {
      return false;
    }
    final File targetFile = new File(ioDir, name);
    SVNStatus status = getFileStatus(vcs, targetFile);

    if (status == null || status.getContentsStatus() == SVNStatusType.STATUS_NONE ||
        status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
      myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(dir, name, null, recursive));
      return false;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_MISSING)) {
      return false;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_DELETED)) {
      SVNNodeKind kind = status.getKind();
      // kind differs.
      if (directory && kind != SVNNodeKind.DIR || !directory && kind != SVNNodeKind.FILE) {
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
      catch (SVNException e) {
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

  public void commandStarted(CommandEvent event) {
    myUndoingMove = false;
    final Project project = event.getProject();
    if (project == null) return;
    commandStarted(project);
  }

  void commandStarted(final Project project) {
    myUndoingMove = false;
    myMoveExceptions.remove(project);
  }

  public void commandFinished(CommandEvent event) {
    final Project project = event.getProject();
    if (project == null) return;
    commandFinished(project);
  }

  void commandFinished(final Project project) {
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
    final List<VirtualFile> toRefreshFiles = new ArrayList<VirtualFile>();
    final List<VirtualFile> toRefreshDirs = new ArrayList<VirtualFile>();
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
    RefreshQueue.getInstance().refresh(true, true, new Runnable() {
      public void run() {
        if (project.isDisposed()) return;
        filterOutInvalid(toRefreshFiles);
        filterOutInvalid(toRefreshDirs);

        final VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
        vcsDirtyScopeManager.filesDirty(toRefreshFiles, toRefreshDirs);
      }
    }, myFilesToRefresh);
    myFilesToRefresh.clear();
  }

  private static void filterOutInvalid(final Collection<VirtualFile> files) {
    for (Iterator<VirtualFile> iterator = files.iterator(); iterator.hasNext();) {
      final VirtualFile file = iterator.next();
      if (! file.isValid() || ! file.exists()) {
        LOG.info("Refresh root is not valid: " + file.getPath());
        iterator.remove();
      }
    }
  }

  private void processAddedFiles(Project project) {
    SvnVcs vcs = SvnVcs.getInstance(project);
    List<VirtualFile> addedVFiles = new ArrayList<VirtualFile>();
    Map<VirtualFile, File> copyFromMap = new HashMap<VirtualFile, File>();
    final Set<VirtualFile> recursiveItems = new HashSet<VirtualFile>();
    fillAddedFiles(project, vcs, addedVFiles, copyFromMap, recursiveItems);
    if (addedVFiles.isEmpty()) return;
    final VcsShowConfirmationOption.Value value = vcs.getAddConfirmation().getValue();
    if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
      final Collection<VirtualFile> filesToProcess = promptAboutAddition(vcs, addedVFiles, value, vcsHelper);
      if (filesToProcess != null && !filesToProcess.isEmpty()) {
        final List<VcsException> exceptions = new ArrayList<VcsException>();
        runInBackground(project, "Adding files to Subversion",
                        createAdditionRunnable(project, vcs, copyFromMap, filesToProcess, exceptions));
        if (!exceptions.isEmpty()) {
          vcsHelper.showErrors(exceptions, SvnBundle.message("add.files.errors.title"));
        }
      }
    }
  }

  private void runInBackground(final Project project, final String name, final Runnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, name, false, project);
    } else {
      runnable.run();
    }
  }

  private Runnable createAdditionRunnable(final Project project,
                               final SvnVcs vcs,
                               final Map<VirtualFile, File> copyFromMap,
                               final Collection<VirtualFile> filesToProcess,
                               final List<VcsException> exceptions) {
    return new Runnable() {
      @Override
      public void run() {
        for(VirtualFile file: filesToProcess) {
          final File ioFile = new File(file.getPath());
          try {
            final File copyFrom = copyFromMap.get(file);
            if (copyFrom != null) {
              try {
                new ActionWithTempFile(ioFile) {
                  protected void executeInternal() throws VcsException {
                    try {
                      // not recursive
                      new RepeatSvnActionThroughBusy() {
                        @Override
                        protected void executeImpl() throws SVNException {
                          try {
                            vcs.getFactory(copyFrom).createCopyMoveClient().copy(copyFrom, ioFile, true, false);
                          }
                          catch (VcsException e) {
                            wrapAndThrow(e);
                          }
                        }
                      }.execute();
                    }
                    catch (SVNException e) {
                      throw new VcsException(e);
                    }
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
                protected void executeImpl() throws SVNException {
                  try {
                    vcs.getFactory(ioFile).createAddClient().add(ioFile, null, false, false, true, null);
                  }
                  catch (VcsException e) {
                    wrapAndThrow(e);
                  }
                }
              }.execute();
            }
            VcsDirtyScopeManager.getInstance(project).fileDirty(file);
          }
          catch (SVNException e) {
            exceptions.add(new VcsException(e));
          }
        }
      }
    };
  }

  private Collection<VirtualFile> promptAboutAddition(SvnVcs vcs,
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
        final SVNStatus fileStatus = getFileStatus(vcs, ioFile);
        if (fileStatus == null || ! SvnVcs.svnStatusIs(fileStatus, SVNStatusType.STATUS_IGNORED)) {
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
    final List<Pair<FilePath, WorkingCopyFormat>> deletedFiles = new ArrayList<Pair<FilePath, WorkingCopyFormat>>();
    final Collection<FilePath> filesToProcess = new ArrayList<FilePath>();
    List<VcsException> exceptions = new ArrayList<VcsException>();
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
        if (filesToProcess != null && ! filesToProcess.isEmpty()) {
          runInBackground(project, "Deleting files from Subversion", createDeleteRunnable(project, vcs, filesToProcess, exceptions));
        }
        final List<FilePath> deletedFilesFiles = ObjectsConvertor.convert(deletedFiles, new Convertor<Pair<FilePath, WorkingCopyFormat>, FilePath>() {
          @Override
          public FilePath convert(Pair<FilePath, WorkingCopyFormat> o) {
            return o.getFirst();
          }
        });
        for (FilePath file : deletedFilesFiles) {
          final FilePath parent = file.getParentPath();
          if (parent != null) {
            myFilesToRefresh.add(parent.getVirtualFile());
          }
        }
        if (filesToProcess != null) {
          deletedFilesFiles.removeAll(filesToProcess);
        }
        for (FilePath file : deletedFilesFiles) {
          FileUtil.delete(file.getIOFile());
        }
      }
    } catch (SVNException e) {
      exceptions.add(new VcsException(e));
    }
    if (! exceptions.isEmpty()) {
      vcsHelper.showErrors(exceptions, SvnBundle.message("delete.files.errors.title"));
    }
  }

  private Runnable createDeleteRunnable(final Project project,
                                        final SvnVcs vcs,
                                        final Collection<FilePath> filesToProcess,
                                        final List<VcsException> exceptions) {
    return new Runnable() {
      public void run() {
        for(FilePath file: filesToProcess) {
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
          catch (SVNException e) {
            exceptions.add(new VcsException(e));
          }
        }
      }
    };
  }

  private Collection<FilePath> promptAboutDeletion(List<Pair<FilePath, WorkingCopyFormat>> deletedFiles,
                                                   SvnVcs vcs,
                                                   VcsShowConfirmationOption.Value value,
                                                   AbstractVcsHelper vcsHelper) {
    final Convertor<Pair<FilePath, WorkingCopyFormat>, FilePath> convertor =
      new Convertor<Pair<FilePath, WorkingCopyFormat>, FilePath>() {
        @Override
        public FilePath convert(Pair<FilePath, WorkingCopyFormat> o) {
          return o.getFirst();
        }
      };
    Collection<FilePath> filesToProcess;
    if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      filesToProcess = ObjectsConvertor.convert(deletedFiles, convertor);
    } else {

      final String singleFilePrompt;
      if (deletedFiles.size() == 1 && deletedFiles.get(0).getFirst().isDirectory()) {
        singleFilePrompt = WorkingCopyFormat.ONE_DOT_SEVEN.equals(deletedFiles.get(0).getSecond()) ?
                           SvnBundle.getString("confirmation.text.delete.dir.17") :
                           SvnBundle.getString("confirmation.text.delete.dir");
      }
      else {
        singleFilePrompt = SvnBundle.getString("confirmation.text.delete.file");
      }
      final Collection<FilePath> files = vcsHelper
        .selectFilePathsToProcess(ObjectsConvertor.convert(deletedFiles, convertor), SvnBundle.message("confirmation.title.delete.multiple.files"), null,
                                  SvnBundle.message("confirmation.title.delete.file"), singleFilePrompt, vcs.getDeleteConfirmation());
      filesToProcess = files == null ? null : new ArrayList<FilePath>(files);
    }
    return filesToProcess;
  }

  private void fillDeletedFiles(Project project, List<Pair<FilePath, WorkingCopyFormat>> deletedFiles, Collection<FilePath> deleteAnyway)
    throws SVNException {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final Collection<File> files = myDeletedFiles.remove(project);
    for (final File file : files) {
      final SVNStatus status = new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() throws SVNException {
          myT = vcs.getFactory(file).createStatusClient().doStatus(file, false);
        }
      }.compute();
      boolean isAdded = SVNStatusType.STATUS_ADDED.equals(status.getNodeStatus());
      final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
      if (isAdded) {
        deleteAnyway.add(filePath);
      } else {
        deletedFiles.add(Pair.create(filePath, vcs.getWorkingCopyFormat(file)));
      }
    }
  }

  private void processMovedFiles(final Project project) {
    if (myMovedFiles.isEmpty()) return;

    final Runnable runnable = new Runnable() {
      public void run() {
        for (Iterator<MovedFileInfo> iterator = myMovedFiles.iterator(); iterator.hasNext();) {
          MovedFileInfo movedFileInfo = iterator.next();
          if (movedFileInfo.myProject == project) {
            doMove(SvnVcs.getInstance(project), movedFileInfo.mySrc, movedFileInfo.myDst);
            iterator.remove();
          }
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
    return new File(vf.getPath()).getAbsoluteFile();
  }

  @Nullable
  private static SVNStatus getFileStatus(@NotNull final SvnVcs vcs, @NotNull final File file) {
    try {
      return new RepeatSvnActionThroughBusy() {
        @Override
        protected void executeImpl() throws SVNException {
          myT = vcs.getFactory(file).createStatusClient().doStatus(file, false);
        }
      }.compute();
    }
    catch (SVNException e) {
      return null;
    }
  }

  private static boolean isUndoOrRedo(@NotNull final Project project) {
    final UndoManager undoManager = UndoManager.getInstance(project);
    return undoManager.isUndoInProgress() || undoManager.isRedoInProgress();
  }

  private static boolean isUndo(SvnVcs vcs) {
    if (vcs == null || vcs.getProject() == null) {
      return false;
    }
    Project p = vcs.getProject();
    return UndoManager.getInstance(p).isUndoInProgress();
  }

  public void afterDone(final ThrowableConsumer<LocalFileOperationsHandler, IOException> invoker) {
  }
}
