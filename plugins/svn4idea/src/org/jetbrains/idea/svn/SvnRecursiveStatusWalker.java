// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.StatusConsumer;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.LinkedList;

import static com.intellij.openapi.vfs.VfsUtilCore.visitChildrenRecursively;
import static com.intellij.openapi.vfs.VirtualFileVisitor.ONE_LEVEL_DEEP;
import static com.intellij.openapi.vfs.VirtualFileVisitor.SKIP_ROOT;
import static com.intellij.util.containers.ContainerUtil.ar;

public class SvnRecursiveStatusWalker {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnRecursiveStatusWalker");

  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final ChangeListManager myChangeListManager;
  @Nullable private final ProgressIndicator myProgress;
  @NotNull private final StatusReceiver myReceiver;
  @NotNull private final LinkedList<MyItem> myQueue;
  @NotNull private final MyHandler myHandler;
  @Nullable private MultiMap<FilePath, FilePath> myNonRecursiveScope;

  public SvnRecursiveStatusWalker(@NotNull SvnVcs vcs, @NotNull StatusReceiver receiver, @Nullable ProgressIndicator progress) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myReceiver = receiver;
    myProgress = progress;
    myQueue = new LinkedList<>();
    myHandler = new MyHandler();
  }

  public void setNonRecursiveScope(@Nullable MultiMap<FilePath, FilePath> nonRecursiveScope) {
    myNonRecursiveScope = nonRecursiveScope;
  }

  public void go(@NotNull FilePath rootPath, @NotNull Depth depth) throws SvnBindException {
    myQueue.add(createItem(rootPath, depth, false));

    while (!myQueue.isEmpty()) {
      checkCanceled();

      MyItem item = myQueue.removeFirst();
      try {
        if (item.getPath().isDirectory()) {
          processDirectory(item);
        }
        else {
          processFile(item);
        }
      }
      catch (SvnBindException e) {
        handleStatusException(item, e);
      }
    }
  }

  private void processDirectory(@NotNull MyItem item) throws SvnBindException {
    File ioFile = item.getPath().getIOFile();

    myHandler.setCurrentItem(item);
    item.getClient().doStatus(ioFile, Revision.WORKING, item.getDepth(), false, false, true, true, myHandler);

    // check if current item was already processed - not to request its status once again
    if (!myHandler.myMetCurrentItem) {
      myHandler.checkIfCopyRootWasReported(myHandler.getCurrentItemStatus());
    }
  }

  private void processFile(@NotNull MyItem item) throws SvnBindException {
    myReceiver.process(item.getPath(), item.getClient().doStatus(item.getPath().getIOFile(), false));
  }

  public void checkCanceled() {
    if (myProgress != null) {
      myProgress.checkCanceled();
    }
  }

  public boolean isIgnoredByVcs(@NotNull final VirtualFile vFile) {
    return ReadAction.compute(() -> {
      if (myVcs.getProject().isDisposed()) throw new ProcessCanceledException();
      return myVcsManager.isIgnored(vFile);
    });
  }

  public boolean isIgnoredIdeaLevel(@NotNull VirtualFile vFile) {
    return myChangeListManager.isIgnoredFile(vFile);
  }

  private void handleStatusException(@NotNull MyItem item, @NotNull SvnBindException e) throws SvnBindException {
    if (e.contains(ErrorCode.WC_NOT_WORKING_COPY) || e.contains(ErrorCode.WC_NOT_FILE) || e.contains(ErrorCode.WC_PATH_NOT_FOUND)) {
      final VirtualFile virtualFile = item.getPath().getVirtualFile();
      if (virtualFile != null && !isIgnoredByVcs(virtualFile)) {
        // self is unversioned
        myReceiver.processUnversioned(virtualFile);

        if (virtualFile.isDirectory()) {
          processRecursively(virtualFile, item.getDepth());
        }
      }
    }
    else {
      throw e;
    }
  }

  private static class MyItem {
    @NotNull private final FilePath myPath;
    @NotNull private final Depth myDepth;
    @NotNull private final StatusClient myStatusClient;
    private final boolean myIsInnerCopyRoot;

    private MyItem(@NotNull FilePath path, @NotNull Depth depth, boolean isInnerCopyRoot, @NotNull StatusClient statusClient) {
      myPath = path;
      myDepth = depth;
      myStatusClient = statusClient;
      myIsInnerCopyRoot = isInnerCopyRoot;
    }

    @NotNull
    public FilePath getPath() {
      return myPath;
    }

    @NotNull
    public Depth getDepth() {
      return myDepth;
    }

    @NotNull
    public StatusClient getClient() {
      return myStatusClient;
    }

    public boolean isIsInnerCopyRoot() {
      return myIsInnerCopyRoot;
    }
  }

  private void processRecursively(@NotNull VirtualFile vFile, @NotNull Depth prevDepth) {
    if (Depth.EMPTY.equals(prevDepth)) return;
    if (isIgnoredIdeaLevel(vFile)) {
      myReceiver.processIgnored(vFile);
      return;
    }

    Depth newDepth = Depth.INFINITY.equals(prevDepth) ? Depth.INFINITY : Depth.EMPTY;
    VirtualFileVisitor.Option[] options = newDepth.equals(Depth.EMPTY) ? ar(SKIP_ROOT, ONE_LEVEL_DEEP) : new VirtualFileVisitor.Option[0];

    visitChildrenRecursively(vFile, new VirtualFileVisitor(options) {
      @NotNull
      @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        if (isIgnoredIdeaLevel(file)) {
          myReceiver.processIgnored(file);
          return SKIP_CHILDREN;
        }
        else if (file.isDirectory() && file.findChild(SvnUtil.SVN_ADMIN_DIR_NAME) != null) {
          myQueue.add(createItem(VcsUtil.getFilePath(file), newDepth, true));
          return SKIP_CHILDREN;
        }
        else {
          myReceiver.processUnversioned(file);
          return CONTINUE;
        }
      }
    });
  }

  @NotNull
  private MyItem createItem(@NotNull FilePath path, @NotNull Depth depth, boolean isInnerCopyRoot) {
    StatusClient statusClient = myVcs.getFactory(path.getIOFile()).createStatusClient(myNonRecursiveScope, createEventHandler());

    return new MyItem(path, depth, isInnerCopyRoot, statusClient);
  }

  @NotNull
  public ProgressTracker createEventHandler() {
    return new ProgressTracker() {
      @Override
      public void consume(ProgressEvent event) {
      }

      @Override
      public void checkCancelled() throws ProcessCanceledException {
        SvnRecursiveStatusWalker.this.checkCanceled();
      }
    };
  }

  private class MyHandler implements StatusConsumer {
    private MyItem myCurrentItem;
    private boolean myMetCurrentItem;

    public void setCurrentItem(@NotNull MyItem currentItem) {
      myCurrentItem = currentItem;
      myMetCurrentItem = false;
    }

    public void checkIfCopyRootWasReported(@Nullable Status status) {
      if (!myMetCurrentItem && status != null && FileUtil.filesEqual(status.getFile(), myCurrentItem.getPath().getIOFile())) {
        myMetCurrentItem = true;

        processCurrentItem(status);
      }
    }

    @Nullable
    public Status getCurrentItemStatus() {
      Status result = null;

      try {
        result = myCurrentItem.getClient().doStatus(myCurrentItem.getPath().getIOFile(), false);
      }
      catch (SvnBindException e) {
        LOG.info(e);
      }

      return result;
    }

    public void processCurrentItem(@NotNull Status status) {
      StatusType nodeStatus = status.getNodeStatus();
      FilePath path = myCurrentItem.getPath();
      VirtualFile vf = path.getVirtualFile();

      if (vf != null) {
        if (StatusType.STATUS_IGNORED.equals(nodeStatus)) {
          myReceiver.processIgnored(vf);
        }
        else if (StatusType.STATUS_UNVERSIONED.equals(nodeStatus) || StatusType.UNKNOWN.equals(nodeStatus)) {
          myReceiver.processUnversioned(vf);
          processRecursively(vf, myCurrentItem.getDepth());
        }
        else if (!StatusType.OBSTRUCTED.equals(nodeStatus) && !StatusType.STATUS_NONE.equals(nodeStatus)) {
          if (myCurrentItem.isIsInnerCopyRoot()) {
            myReceiver.processCopyRoot(vf, status.getURL(), myVcs.getWorkingCopyFormat(path.getIOFile()), status.getRepositoryRootURL());
          }
          else {
            myReceiver.bewareRoot(vf, status.getURL());
          }
        }
      }
    }

    @Override
    public void consume(final Status status) throws SvnBindException {
      checkCanceled();
      final File ioFile = status.getFile();
      checkIfCopyRootWasReported(status);

      VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
      if (vFile != null && isIgnoredByVcs(vFile)) return;
      if (myProject.isDisposed()) throw new ProcessCanceledException();

      if (vFile != null && status.is(StatusType.STATUS_UNVERSIONED)) {
        if (vFile.isDirectory()) {
          if (!FileUtil.filesEqual(myCurrentItem.getPath().getIOFile(), ioFile)) {
            myQueue.add(createItem(VcsUtil.getFilePath(vFile), Depth.INFINITY, true));
          }
        }
        else {
          myReceiver.processUnversioned(vFile);
        }
      }
      else {
        myReceiver.process(VcsUtil.getFilePath(ioFile, status.getKind().isDirectory()), status);
      }
    }
  }
}
