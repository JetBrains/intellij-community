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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.StatusConsumer;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.LinkedList;

public class SvnRecursiveStatusWalker {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnRecursiveStatusWalker");
  private final StatusWalkerPartner myPartner;
  private final SvnVcs myVcs;
  private final Project myProject;
  private final StatusReceiver myReceiver;
  private final LinkedList<MyItem> myQueue;
  private final MyHandler myHandler;

  public SvnRecursiveStatusWalker(final SvnVcs vcs, final StatusReceiver receiver, final StatusWalkerPartner partner) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myReceiver = receiver;
    myPartner = partner;
    myQueue = new LinkedList<MyItem>();
    myHandler = new MyHandler();
  }

  public void go(final FilePath rootPath, final Depth depth) throws SvnBindException {
    final MyItem root = createItem(rootPath, depth, false);
    myQueue.add(root);

    while (! myQueue.isEmpty()) {
      myPartner.checkCanceled();

      final MyItem item = myQueue.removeFirst();
      final FilePath path = item.getPath();
      final File ioFile = path.getIOFile();

      if (path.isDirectory()) {
        myHandler.setCurrentItem(item);
        try {
          final StatusClient client = item.getClient();
          client.doStatus(ioFile, SVNRevision.WORKING, item.getDepth(), false, false, true, true, myHandler, null);
          myHandler.checkIfCopyRootWasReported(null, ioFile);
        }
        catch (SvnBindException e) {
          handleStatusException(item, path, e);
        }
      } else {
        try {
          final Status status = item.getClient().doStatus(ioFile, false);
          myReceiver.process(path, status);
        }
        catch (SvnBindException e) {
          handleStatusException(item, path, e);
        }
        catch (SVNException e) {
          handleStatusException(item, path, new SvnBindException(e));
        }
      }
    }
  }

  private void handleStatusException(MyItem item, FilePath path, SvnBindException e) throws SvnBindException {
    if (e.contains(SVNErrorCode.WC_NOT_DIRECTORY) || e.contains(SVNErrorCode.WC_NOT_FILE)) {
      final VirtualFile virtualFile = path.getVirtualFile();
      if (virtualFile != null) {
        if (! myPartner.isIgnoredByVcs(virtualFile)) {
          // self is unversioned
          myReceiver.processUnversioned(virtualFile);

          if (virtualFile.isDirectory()) {
            processRecursively(virtualFile, item.getDepth());
          }
        }
      }
    } else {
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

  private void processRecursively(final VirtualFile vFile, final Depth prevDepth) {
    if (Depth.EMPTY.equals(prevDepth)) return;
    if (myPartner.isIgnoredIdeaLevel(vFile)) {
      myReceiver.processIgnored(vFile);
      return;
    }
    final Depth newDepth = Depth.INFINITY.equals(prevDepth) ? Depth.INFINITY : Depth.EMPTY;

    final File ioFile = new File(vFile.getPath());
    final Processor<File> processor;
    final Processor<File> directoryFilter;
    final Ref<File> lastIgnored = new Ref<File>();
    final Processor<File> checkDirProcessor = new Processor<File>() {
      @Override
      public boolean process(File file) {
        final FilePathImpl path = new FilePathImpl(file, true);
        path.refresh();
        path.hardRefresh();
        VirtualFile vf = path.getVirtualFile();
        if (vf != null && myPartner.isIgnoredIdeaLevel(vf)) {
          lastIgnored.set(file);
          myReceiver.processIgnored(vf);
          return true;
        }
        if (file.isDirectory() && new File(file, SVNFileUtil.getAdminDirectoryName()).exists()) {
          final MyItem childItem = createItem(path, newDepth, true);
          myQueue.add(childItem);
        } else if (vf != null) {
          myReceiver.processUnversioned(vf);
        }
        return true;
      }
    };
    if (Depth.EMPTY.equals(newDepth)) {
      // just process immediate children - so only root directory itself should satisfy filter
      directoryFilter = new Processor<File>() {
        @Override
        public boolean process(File file) {
          return FileUtil.filesEqual(ioFile, file);
        }
      };
      processor = new Processor<File>() {
        @Override
        public boolean process(File file) {
          // TODO: check if we should still call checkDirProcessor() here - or we really could not check ignore settings but just call
          // TODO: myReceiver.processUnversioned() for all immediate children
          // here we deal only with immediate children - so ignored on IDEA level for children is not important
          return FileUtil.filesEqual(ioFile, file) || checkDirProcessor.process(file);
        }
      };
    } else {
      directoryFilter = new Processor<File>() {
        @Override
        public boolean process(File file) {
          return ! Comparing.equal(lastIgnored, file) && (myQueue.isEmpty() || ! FileUtil.filesEqual(myQueue.getLast().getPath().getIOFile(), file));
        }
      };
      processor = checkDirProcessor;
    }
    FileUtil.processFilesRecursively(ioFile, processor, directoryFilter);
  }

  @NotNull
  private MyItem createItem(@NotNull FilePath path, @NotNull Depth depth, boolean isInnerCopyRoot) {
    StatusClient statusClient =
      myVcs.getFactory(path.getIOFile()).createStatusClient(myPartner.getFileProvider(), myPartner.getEventHandler());

    return new MyItem(path, depth, isInnerCopyRoot, statusClient);
  }

  private class MyHandler implements StatusConsumer {
    private MyItem myCurrentItem;
    private boolean myMetCurrentItem;

    public void setCurrentItem(MyItem currentItem) {
      myCurrentItem = currentItem;
      myMetCurrentItem = false;
    }

    public void checkIfCopyRootWasReported(@Nullable final Status ioFileStatus, final File ioFile) {
      File itemFile = myCurrentItem.getPath().getIOFile();
      if (! myMetCurrentItem && FileUtil.filesEqual(ioFile, itemFile)) {
        myMetCurrentItem = true;
        Status statusInner;
        try {
          statusInner = ioFileStatus != null ? ioFileStatus : myCurrentItem.getClient().doStatus(itemFile, false);
        }
        catch (SvnBindException e) {
          LOG.info(e);
          statusInner = null;
        }
        if (statusInner == null)  return;

        final StatusType status = statusInner.getNodeStatus();
        final VirtualFile vf = myCurrentItem.getPath().getVirtualFile();
        if (StatusType.STATUS_IGNORED.equals(status)) {
          if (vf != null) {
            myReceiver.processIgnored(vf);
          }
          return;
        }
        if (StatusType.STATUS_UNVERSIONED.equals(status) || StatusType.UNKNOWN.equals(status)) {
          if (vf != null) {
            myReceiver.processUnversioned(vf);
            processRecursively(vf, myCurrentItem.getDepth());
          }
          return;
        }
        if (StatusType.OBSTRUCTED.equals(status) || StatusType.STATUS_NONE.equals(status)) {
          return;
        }
        if (vf != null) {
          if (myCurrentItem.isIsInnerCopyRoot()) {
            myReceiver.processCopyRoot(vf, statusInner.getURL(), myVcs.getWorkingCopyFormat(ioFile), statusInner.getRepositoryRootURL());
          } else {
            myReceiver.bewareRoot(vf, statusInner.getURL());
          }
        }
      }
    }

    @Override
    public void consume(final Status status) throws SVNException {
      myPartner.checkCanceled();
      final File ioFile = status.getFile();
      checkIfCopyRootWasReported(status, ioFile);

      final VirtualFile vFile = getVirtualFile(ioFile);
      if (vFile != null) {
        final Boolean excluded = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            if (myProject.isDisposed()) return null;
            return myPartner.isIgnoredByVcs(vFile);
          }
        });
        if (Boolean.TRUE.equals(excluded)) return;
      }
      if (myProject.isDisposed()) throw new ProcessCanceledException();

      if ((vFile != null) && (status.is(StatusType.STATUS_UNVERSIONED))) {
        if (vFile.isDirectory()) {
          if (FileUtil.filesEqual(myCurrentItem.getPath().getIOFile(), ioFile)) {
            //myReceiver.processUnversioned(vFile);
            //processRecursively(vFile, myCurrentItem.getDepth());
          } else {
            final MyItem childItem = createItem(new FilePathImpl(vFile), Depth.INFINITY, true);
            myQueue.add(childItem);
          }
        } else {
          myReceiver.processUnversioned(vFile);
        }
      } else {
        final FilePath path = VcsUtil.getFilePath(ioFile, status.getKind().isDirectory());
        myReceiver.process(path, status);
      }
    }
  }

  private static VirtualFile getVirtualFile(File ioFile) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile vFile = lfs.findFileByIoFile(ioFile);
    if (vFile == null) {
      vFile = lfs.refreshAndFindFileByIoFile(ioFile);
    }
    return vFile;
  }
}
