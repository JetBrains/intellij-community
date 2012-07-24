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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.ActionWithTempFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
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

  private void addToMoveExceptions(final Project project, final SVNException e) {
    List<VcsException> exceptionList = myMoveExceptions.get(project);
    if (exceptionList == null) {
      exceptionList = new ArrayList<VcsException>();
      myMoveExceptions.put(project, exceptionList);
    }
    VcsException vcsException;
    if (SVNErrorCode.ENTRY_EXISTS.equals(e.getErrorMessage().getErrorCode())) {
      vcsException = new VcsException(Arrays.asList("Target of move operation is already under version control.",
                                                    "Subversion move had not been performed. ", e.getMessage()));
    } else {
      vcsException = new VcsException(e);
    }
    exceptionList.add(vcsException);
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
    final boolean dstDirUnderControl = SVNWCUtil.isVersionedDirectory(destFile.getParentFile());
    if (! dstDirUnderControl && !isPendingAdd(vcs.getProject(), toDir)) {
      return null;
    }

    if (!SVNWCUtil.isVersionedDirectory(srcFile.getParentFile())) {
      myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(toDir, copyName, null, false));
      return null;
    }

    final SVNStatus fileStatus = getFileStatus(vcs, srcFile);
    if (fileStatus != null && fileStatus.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
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
    private final SVNWCClient myWcClient;

    private UUIDHelper(final SvnVcs vcs) {
      myWcClient = vcs.createWCClient();
    }

    /**
     * passed dir must be under VC control (it is assumed)
     */
    @Nullable
    public String getRepositoryUUID(final Project project, final VirtualFile dir) {
      try {
        final SVNInfo info1 = myWcClient.doInfo(new File(dir.getPath()), SVNRevision.WORKING);
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
    SVNMoveClient mover = vcs.createMoveClient();
    long srcTime = src.lastModified();
    try {
      // delete old??? (deleted in listener, but we could do it here)
      if (isUndo(vcs)) {
        myUndoingMove = true;
        restoreFromUndoStorage(dst);
        mover.undoMove(src, dst);
      }
      else {
        // if src is not under version control, do usual move.
        SVNStatus srcStatus = getFileStatus(vcs, src);
        if (srcStatus == null || srcStatus.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                srcStatus.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL ||
                srcStatus.getContentsStatus() == SVNStatusType.STATUS_MISSING ||
                srcStatus.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
            return false;
        }
        final String list = SvnChangelistListener.getCurrentMapping(vcs.getProject(), src);
        // todo move back?
        mover.doMove(src, dst);
        if (list != null) {
          SvnChangelistListener.putUnderList(vcs.getProject(), list, dst);
        }
      }
      dst.setLastModified(srcTime);
    }
    catch (SVNException e) {
      addToMoveExceptions(vcs.getProject(), e);
      return false;
    }
    return true;
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
    SvnVcs vcs = getVCS(file);
    if (vcs != null && SvnUtil.isAdminDirectory(file)) {
      return true;
    }
    File ioFile = getIOFile(file);
    if (!SVNWCUtil.isVersionedDirectory(ioFile.getParentFile())) {
      return false;
    }
    try {
      if (SVNWCUtil.isWorkingCopyRoot(ioFile)) {
        return false;
      }
    } catch (SVNException e) {
        //
    }

    SVNStatus status = getFileStatus(ioFile);

    if (status == null ||
        status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
        status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED ||
        status.getContentsStatus() == SVNStatusType.STATUS_MISSING ||
        status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL ||
        status.getContentsStatus() == SVNStatusType.STATUS_IGNORED) {
      return false;
    } else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      if (isUndo(vcs)) {
        moveToUndoStorage(file);
      }
      return true;
    }
    else {
      if (vcs != null) {
        if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
          try {
            final SVNWCClient wcClient = vcs.createWCClient();
            wcClient.doRevert(ioFile, false);
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
    if (isUndo(vcs) && SvnUtil.isAdminDirectory(dir, name)) {
      return false;      
    }
    File ioDir = getIOFile(dir);
    boolean pendingAdd = isPendingAdd(vcs.getProject(), dir);
    if (!SVNWCUtil.isVersionedDirectory(ioDir) && !pendingAdd) {
      return false;
    }
    SVNWCClient wcClient = vcs.createWCClient();
    File targetFile = new File(ioDir, name);
    SVNStatus status = getFileStatus(vcs, targetFile);

    if (status == null || status.getContentsStatus() == SVNStatusType.STATUS_NONE) {
      myAddedFiles.putValue(vcs.getProject(), new AddedFileInfo(dir, name, null, recursive));
      return false;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MISSING) {
      return false;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      SVNNodeKind kind = status.getKind();
      // kind differs.
      if (directory && kind != SVNNodeKind.DIR || !directory && kind != SVNNodeKind.FILE) {
        return false;
      }
      try {
        if (isUndo(vcs)) {
          wcClient.doRevert(targetFile, false);
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
    final RefreshSession session = RefreshQueue.getInstance().createSession(true, true, new Runnable() {
      public void run() {
        if (project.isDisposed()) return;
        filterOutInvalid(toRefreshFiles);
        filterOutInvalid(toRefreshDirs);

        final VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
        vcsDirtyScopeManager.filesDirty(toRefreshFiles, toRefreshDirs);
      }
    });
    filterOutInvalid(myFilesToRefresh);
    session.addAllFiles(myFilesToRefresh);
    session.launch();
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
        SVNWCClient wcClient = vcs.createWCClient();
        final SVNCopyClient copyClient = vcs.createCopyClient();
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
                      final SVNCopySource[] copySource = {new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, copyFrom)};
                      copyClient.doCopy(copySource, ioFile, false, true, true);
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
              wcClient.doAdd(ioFile, true, false, false, true);
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
        if (fileStatus == null || fileStatus.getContentsStatus() != SVNStatusType.STATUS_IGNORED) {
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
    final List<FilePath> deletedFiles = new ArrayList<FilePath>();
    fillDeletedFiles(project, deletedFiles);
    if (deletedFiles.isEmpty() || myUndoingMove) return;
    SvnVcs vcs = SvnVcs.getInstance(project);
    final VcsShowConfirmationOption.Value value = vcs.getDeleteConfirmation().getValue();
    if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
      Collection<FilePath> filesToProcess;
      filesToProcess = promptAboutDeletion(deletedFiles, vcs, value, vcsHelper);
      if (filesToProcess != null && !filesToProcess.isEmpty()) {
        List<VcsException> exceptions = new ArrayList<VcsException>();
        runInBackground(project, "Deleting files from Subversion", createDeleteRunnable(project, vcs, filesToProcess, exceptions));
        if (!exceptions.isEmpty()) {
          vcsHelper.showErrors(exceptions, SvnBundle.message("delete.files.errors.title"));
        }
      }
      for (FilePath file : deletedFiles) {
        final FilePath parent = file.getParentPath();
        if (parent != null) {
          myFilesToRefresh.add(parent.getVirtualFile());
        }
      }
      if (filesToProcess != null) {
        deletedFiles.removeAll(filesToProcess);
      }
      for (FilePath file : deletedFiles) {
        FileUtil.delete(file.getIOFile());
      }
    }
  }

  private Runnable createDeleteRunnable(final Project project,
                                        final SvnVcs vcs,
                                        final Collection<FilePath> filesToProcess,
                                        final List<VcsException> exceptions) {
    return new Runnable() {
      public void run() {
        SVNWCClient wcClient = vcs.createWCClient();
        for(FilePath file: filesToProcess) {
          VirtualFile vFile = file.getVirtualFile();  // for deleted directories
          File ioFile = new File(file.getPath());
          try {
            wcClient.doDelete(ioFile, true, false);
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

  private Collection<FilePath> promptAboutDeletion(List<FilePath> deletedFiles,
                                                   SvnVcs vcs,
                                                   VcsShowConfirmationOption.Value value,
                                                   AbstractVcsHelper vcsHelper) {
    Collection<FilePath> filesToProcess;
    if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      filesToProcess = new ArrayList<FilePath>(deletedFiles);
    }
    else {

      final String singleFilePrompt;
      if (deletedFiles.size() == 1 && deletedFiles.get(0).isDirectory()) {
        singleFilePrompt = SvnBundle.getString("confirmation.text.delete.dir");
      }
      else {
        singleFilePrompt = SvnBundle.getString("confirmation.text.delete.file");
      }
      final Collection<FilePath> files = vcsHelper
        .selectFilePathsToProcess(deletedFiles, SvnBundle.message("confirmation.title.delete.multiple.files"), null,
                                  SvnBundle.message("confirmation.title.delete.file"), singleFilePrompt, vcs.getDeleteConfirmation());
      filesToProcess = files == null ? null : new ArrayList<FilePath>(files);
    }
    return filesToProcess;
  }

  private void fillDeletedFiles(Project project, List<FilePath> deletedFiles) {
    final Collection<File> files = myDeletedFiles.remove(project);
    for (File file : files) {
      final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
      deletedFiles.add(filePath);
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
  private static SVNStatus getFileStatus(File file) {
    final SVNClientManager clientManager = SVNClientManager.newInstance();
    try {
      SVNStatusClient stClient = clientManager.getStatusClient();
      return getFileStatus(file, stClient);
    }
    finally {
      clientManager.dispose();
    }
  }

  @Nullable
  private static SVNStatus getFileStatus(SvnVcs vcs, File file) {
    SVNStatusClient stClient = vcs.createStatusClient();
    return getFileStatus(file, stClient);
  }

  @Nullable
  private static SVNStatus getFileStatus(final File file, final SVNStatusClient stClient) {
    try {
      return stClient.doStatus(file, false);
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
