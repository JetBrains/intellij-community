/**
 * @copyright
 * ====================================================================
 * Copyright (c) 2003-2004 QintSoft.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://subversion.tigris.org/license-1.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 *
 * This software consists of voluntary contributions made by many
 * individuals.  For exact contribution history, see the revision
 * history and logs, available at http://svnup.tigris.org/.
 * ====================================================================
 * @endcopyright
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.peer.PeerFactory;
import com.intellij.vcsUtil.ActionWithTempFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SvnFileSystemListener implements LocalFileOperationsHandler, CommandListener {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnFileSystemListener");

  private static class AddedFileInfo {
    private final Project myProject;
    private final VirtualFile myDir;
    private final String myName;
    @Nullable private final File myCopyFrom;

    public AddedFileInfo(final Project project, final VirtualFile dir, final String name, @Nullable final File copyFrom) {
      myProject = project;
      myDir = dir;
      myName = name;
      myCopyFrom = copyFrom;
    }
  }

  private static class DeletedFileInfo {
    private final Project myProject;
    private final File myFile;

    public DeletedFileInfo(final Project project, final File file) {
      myProject = project;
      myFile = file;
    }
  }

  private List<AddedFileInfo> myAddedFiles = new ArrayList<AddedFileInfo>();
  private List<DeletedFileInfo> myDeletedFiles = new ArrayList<DeletedFileInfo>();
  private List<VirtualFile> myFilesToRefresh = new ArrayList<VirtualFile>();
  @Nullable private File myStorageForUndo;
  private List<Pair<File, File>> myUndoStorageContents = new ArrayList<Pair<File, File>>();

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
    if (!SVNWCUtil.isVersionedDirectory(destFile.getParentFile())) {
      return null;
    }

    if (!SVNWCUtil.isVersionedDirectory(srcFile.getParentFile())) {
      myAddedFiles.add(new AddedFileInfo(vcs.getProject(), toDir, copyName, null));
      return null;
    }
    final SVNStatus fileStatus = getFileStatus(vcs, srcFile);
    if (fileStatus != null && fileStatus.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
      myAddedFiles.add(new AddedFileInfo(vcs.getProject(), toDir, copyName, null));
      return null;
    }

    myAddedFiles.add(new AddedFileInfo(vcs.getProject(), toDir, copyName, srcFile));
    return null;
  }

  public boolean move(VirtualFile file, VirtualFile toDir) throws IOException {
    File srcFile = getIOFile(file);
    File dstFile = new File(getIOFile(toDir), file.getName());

    SvnVcs vcs = getVCS(toDir);
    if (vcs == null) {
      vcs = getVCS(file);
    }
    if (vcs == null) {
      return false;
    }
    final VirtualFile oldParent = file.getParent();
    myFilesToRefresh.add(oldParent);
    myFilesToRefresh.add(toDir);
    return doMove(vcs, srcFile, dstFile);
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
      if (isUndo(vcs)) {
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
        mover.doMove(src, dst);
      }
      dst.setLastModified(srcTime);
    }
    catch (SVNException e) {
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
    return createItem(dir, name, false);
  }

  public boolean createDirectory(VirtualFile dir, String name) throws IOException {
    return createItem(dir, name, true);
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
    if (vcs != null) {
      // never allow to delete admin directories by themselves (this can happen during LVCS undo,
      // which deletes created directories from bottom to top)
      if (file.getName().equals(SVNFileUtil.getAdminDirectoryName())) {
        //moveToUndoStorage(file);
        return true;
      }
      VirtualFile parent = file.getParent();
      if (parent != null) {
        if (parent.getName().equals(SVNFileUtil.getAdminDirectoryName())) {
          //moveToUndoStorage(file);
          return true;
        }
        parent = parent.getParent();
        if (parent != null && parent.getName().equals(SVNFileUtil.getAdminDirectoryName())) {
          //moveToUndoStorage(file);
          return true;
        }
      }
    }
    File ioFile = getIOFile(file);
    if (!SVNWCUtil.isVersionedDirectory(ioFile.getParentFile())) {
      return false;
    }
    else {
        try {
        if (SVNWCUtil.isWorkingCopyRoot(ioFile)) {
          return false;
        }
        } catch (SVNException e) {
            //
        }
    }

    SVNStatus status = getFileStatus(ioFile);

    if (status == null ||
        status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
        status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED ||
        status.getContentsStatus() == SVNStatusType.STATUS_MISSING ||
        status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL) {
      return false;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
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
          myDeletedFiles.add(new DeletedFileInfo(vcs.getProject(), ioFile));
          // packages deleted from disk should not be deleted from svn (IDEADEV-16066)
          if (file.isDirectory()) return true;
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
  private boolean createItem(VirtualFile dir, String name, boolean directory) throws IOException {
    SvnVcs vcs = getVCS(dir);
    if (vcs == null) {
      return false;
    }
    File ioDir = getIOFile(dir);
    boolean pendingAdd = isPendingAdd(dir);
    if (!SVNWCUtil.isVersionedDirectory(ioDir) && !pendingAdd) {
      return false;
    }
    SVNWCClient wcClient = vcs.createWCClient();
    File targetFile = new File(ioDir, name);
    SVNStatus status = getFileStatus(vcs, targetFile);

    if (status == null || status.getContentsStatus() == SVNStatusType.STATUS_NONE) {
      myAddedFiles.add(new AddedFileInfo(vcs.getProject(), dir, name, null));
      return false;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MISSING) {
      return false;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      SVNNodeKind kind = status.getKind();
      // kind differs.
      if ((directory && kind != SVNNodeKind.DIR) || (!directory && kind != SVNNodeKind.FILE)) {
        return false;
      }
      try {
        if (isUndo(vcs)) {
          wcClient.doRevert(targetFile, false);
          return true;
        }
        myAddedFiles.add(new AddedFileInfo(vcs.getProject(), dir, name, null));
        return false;
      }
      catch (SVNException e) {
        SVNFileUtil.deleteAll(targetFile, true);
        return false;
      }
    }
    return false;
  }

  private boolean isPendingAdd(final VirtualFile dir) {
    for(AddedFileInfo i: myAddedFiles) {
      if (i.myDir == dir.getParent() && i.myName == dir.getName()) {
        return true;
      }
    }
    return false;
  }

  public void commandStarted(CommandEvent event) {
  }

  public void beforeCommandFinished(CommandEvent event) {
  }

  public void commandFinished(CommandEvent event) {
    final Project project = event.getProject();
    if (project == null) return;
    if (myAddedFiles.size() > 0) {
      processAddedFiles(project);
    }
    if (myDeletedFiles.size() > 0) {
      processDeletedFiles(project);
    }
    if (myFilesToRefresh.size() > 0) {
      final List<VirtualFile> toRefresh = new ArrayList<VirtualFile>(myFilesToRefresh);
      final RefreshSession session = RefreshQueue.getInstance().createSession(true, true, new Runnable() {
        public void run() {
          if (project.isDisposed()) return;
          for(VirtualFile f: toRefresh) {
            if (!f.isValid()) continue;
            if (f.isDirectory()) {
              VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(f, true);
            }
            else {
              VcsDirtyScopeManager.getInstance(project).fileDirty(f);
            }
          }
        }
      });
      session.addAllFiles(myFilesToRefresh);
      session.launch();
      myFilesToRefresh.clear();
    }
  }

  private void processAddedFiles(Project project) {
    SvnVcs vcs = SvnVcs.getInstance(project);
    List<VirtualFile> addedVFiles = new ArrayList<VirtualFile>();
    Map<VirtualFile, File> copyFromMap = new HashMap<VirtualFile, File>();
    for (Iterator<AddedFileInfo> it = myAddedFiles.iterator(); it.hasNext();) {
      AddedFileInfo addedFileInfo = it.next();
      if (addedFileInfo.myProject == project) {
        it.remove();
        VirtualFile addedFile = addedFileInfo.myDir.findChild(addedFileInfo.myName);
        if (addedFile != null) {
          final SVNStatus fileStatus = getFileStatus(vcs, new File(getIOFile(addedFileInfo.myDir), addedFileInfo.myName));
          if (fileStatus == null || fileStatus.getContentsStatus() != SVNStatusType.STATUS_IGNORED) {
            boolean isIgnored = ChangeListManager.getInstance(addedFileInfo.myProject).isIgnoredFile(addedFile);
            if (!isIgnored) {
              addedVFiles.add(addedFile);
              copyFromMap.put(addedFile, addedFileInfo.myCopyFrom);
            }
          }
        }
      }
    }
    if (addedVFiles.size() == 0) return;
    final VcsShowConfirmationOption.Value value = vcs.getAddConfirmation().getValue();
    if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
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
                                                        vcs.getAddConfirmation());
      }
      if (filesToProcess != null) {
        final List<VcsException> exceptions = new ArrayList<VcsException>();
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
                      copyClient.doCopy(copyFrom, SVNRevision.WORKING, ioFile, false, false);
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
              wcClient.doAdd(ioFile, false, false, false, false);
            }
            VcsDirtyScopeManager.getInstance(project).fileDirty(file);
          }
          catch (SVNException e) {
            exceptions.add(new VcsException(e));
          }
        }
        if (!exceptions.isEmpty()) {
          vcsHelper.showErrors(exceptions, "Errors Adding Files");
        }
      }
    }
  }

  private void processDeletedFiles(Project project) {
    final List<FilePath> deletedFiles = new ArrayList<FilePath>();
    for (Iterator<DeletedFileInfo> it = myDeletedFiles.iterator(); it.hasNext();) {
      DeletedFileInfo deletedFileInfo = it.next();
      if (deletedFileInfo.myProject == project) {
        it.remove();
        final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(deletedFileInfo.myFile);
        deletedFiles.add(filePath);
      }
    }
    if (deletedFiles.size() == 0) return;
    SvnVcs vcs = SvnVcs.getInstance(project);
    final VcsShowConfirmationOption.Value value = vcs.getDeleteConfirmation().getValue();
    if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
      Collection<FilePath> filesToProcess;
      if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
        filesToProcess = deletedFiles;
      }
      else {

        final String singleFilePrompt;
        if (deletedFiles.size() == 1 && deletedFiles.get(0).isDirectory()) {
          singleFilePrompt = SvnBundle.message("confirmation.text.delete.dir");
        }
        else {
          singleFilePrompt = SvnBundle.message("confirmation.text.delete.file");
        }
        filesToProcess = vcsHelper.selectFilePathsToProcess(deletedFiles, SvnBundle.message("confirmation.title.delete.multiple.files"),
                                                            null,
                                                            SvnBundle.message("confirmation.title.delete.file"), singleFilePrompt,
                                                            vcs.getAddConfirmation());
      }
      if (filesToProcess != null) {
        List<VcsException> exceptions = new ArrayList<VcsException>();
        SVNWCClient wcClient = vcs.createWCClient();
        for(FilePath file: filesToProcess) {
          VirtualFile vFile = file.getVirtualFile();  // for deleted directories
          File ioFile = new File(file.getPath());
          try {
            wcClient.doDelete(ioFile, true, false);
            if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
              vFile.refresh(true, true);
              VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(vFile, true);
            }
            else {
              VcsDirtyScopeManager.getInstance(project).fileDirty(file);
            }
          }
          catch (SVNException e) {
            exceptions.add(new VcsException(e));
          }
        }
        if (!exceptions.isEmpty()) {
          vcsHelper.showErrors(exceptions, "Errors Deleting Files");
        }
      }
    }
  }

  public void undoTransparentActionStarted() {
  }

  public void undoTransparentActionFinished() {
  }

  @Nullable
  private static SvnVcs getVCS(VirtualFile file) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; projects != null && i < projects.length; i++) {
      Project project = projects[i];
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

  private static boolean isUndo(SvnVcs vcs) {
    if (vcs == null || vcs.getProject() == null) {
      return false;
    }
    Project p = vcs.getProject();
    return UndoManager.getInstance(p).isUndoInProgress();
  }
}
