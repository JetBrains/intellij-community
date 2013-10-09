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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.SvnCommandLineStatusClient;
import org.jetbrains.idea.svn.portable.JavaHLSvnStatusClient;
import org.jetbrains.idea.svn.portable.SvnStatusClientI;
import org.jetbrains.idea.svn.portable.SvnkitSvnStatusClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.*;

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

  public void go(final FilePath rootPath, final SVNDepth depth) throws SVNException {
    final MyItem root = new MyItem(myVcs, rootPath, depth, myPartner.createStatusClient(), false);
    myQueue.add(root);

    while (! myQueue.isEmpty()) {
      myPartner.checkCanceled();

      final MyItem item = myQueue.removeFirst();
      final FilePath path = item.getPath();
      final File ioFile = path.getIOFile();

      if (path.isDirectory()) {
        myHandler.setCurrentItem(item);
        try {
          final SvnStatusClientI client = item.getClient(ioFile);
          client.doStatus(ioFile, SVNRevision.WORKING, item.getDepth(), false, false, true, true, myHandler, null);
          myHandler.checkIfCopyRootWasReported(null, ioFile);
        }
        catch (SVNException e) {
          handleStatusException(item, path, e);
        }
      } else {
        try {
          final SVNStatus status = item.getClient(ioFile).doStatus(ioFile, false, false);
          myReceiver.process(path, status);
        } catch (SVNException e) {
          handleStatusException(item, path, e);
        }
      }
    }
  }

  private void handleStatusException(MyItem item, FilePath path, SVNException e) throws SVNException {
    final SVNErrorCode errorCode = e.getErrorMessage().getErrorCode();
    if (SVNErrorCode.WC_NOT_DIRECTORY.equals(errorCode) || SVNErrorCode.WC_NOT_FILE.equals(errorCode)) {
      final VirtualFile virtualFile = path.getVirtualFile();
      if (virtualFile != null) {
        if (! myPartner.isExcluded(virtualFile)) {
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
    private final Project myProject;
    private final FilePath myPath;
    private final SVNDepth myDepth;
    private final SvnStatusClientI mySvnClient;
    private final SvnStatusClientI myCommandLineClient;
    private final boolean myIsInnerCopyRoot;
    private final SvnConfiguration myConfiguration17;
    private final SvnVcs myVcs;

    private MyItem(SvnVcs vcs, FilePath path, SVNDepth depth, SVNStatusClient client, boolean isInnerCopyRoot) {
      myVcs = vcs;
      myProject = vcs.getProject();
      myConfiguration17 = SvnConfiguration.getInstance(myProject);
      myPath = path;
      myDepth = depth;
      mySvnClient = new SvnkitSvnStatusClient(myVcs, client);
      myCommandLineClient = new SvnCommandLineStatusClient(myVcs);
      myIsInnerCopyRoot = isInnerCopyRoot;
    }

    public FilePath getPath() {
      return myPath;
    }

    public SVNDepth getDepth() {
      return myDepth;
    }

    public SvnStatusClientI getClient(final File file) {
      // TODO: refactor to ClientFactory usage but carefully save all parameters passed in myClient - fileProvider and
      // TODO: event handler (for cancel support)
      WorkingCopyFormat format = myVcs.getWorkingCopyFormat(file);

      if (format == WorkingCopyFormat.ONE_DOT_EIGHT) {
        return myCommandLineClient;
      }

      if (format == WorkingCopyFormat.ONE_DOT_SIX) {
        return mySvnClient;
      }

      // check format
      if (CheckJavaHL.isPresent() && SvnConfiguration.UseAcceleration.javaHL.equals(myConfiguration17.myUseAcceleration) &&
          Svn17Detector.is17(myProject, file)) {
        return new JavaHLSvnStatusClient(myProject);
      } else if (myConfiguration17.isCommandLine()) {
        // apply command line disregarding working copy format
        return myCommandLineClient;
      }
      return mySvnClient;
    }

    public boolean isIsInnerCopyRoot() {
      return myIsInnerCopyRoot;
    }
  }

  private void processRecursively(final VirtualFile vFile, final SVNDepth prevDepth) {
    if (SVNDepth.EMPTY.equals(prevDepth)) return;
    if (myPartner.isIgnoredIdeaLevel(vFile)) {
      myReceiver.processIgnored(vFile);
      return;
    }
    final SVNDepth newDepth = SVNDepth.INFINITY.equals(prevDepth) ? SVNDepth.INFINITY : SVNDepth.EMPTY;

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
          final MyItem childItem = new MyItem(myVcs, path, newDepth, myPartner.createStatusClient(), true);
          myQueue.add(childItem);
        } else if (vf != null) {
          myReceiver.processUnversioned(vf);
        }
        return true;
      }
    };
    if (SVNDepth.EMPTY.equals(newDepth)) {
      directoryFilter = Processor.TRUE;
      processor = new Processor<File>() {
        @Override
        public boolean process(File file) {
          // here we deal only with immediate children - so ignored on IDEA level for children is not important - we nevertheless do not go into
          // other levels
          if (! FileUtil.filesEqual(ioFile, file)) return true;
          if (! FileUtil.filesEqual(ioFile, file.getParentFile())) return false;
          return checkDirProcessor.process(file);
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

  private class MyHandler implements ISVNStatusHandler {
    private MyItem myCurrentItem;
    private boolean myMetCurrentItem;

    public void setCurrentItem(MyItem currentItem) {
      myCurrentItem = currentItem;
      myMetCurrentItem = false;
    }

    public void checkIfCopyRootWasReported(@Nullable final SVNStatus ioFileStatus, final File ioFile) {
      File itemFile = myCurrentItem.getPath().getIOFile();
      if (! myMetCurrentItem && FileUtil.filesEqual(ioFile, itemFile)) {
        myMetCurrentItem = true;
        SVNStatus statusInner;
        try {
          statusInner = ioFileStatus != null ? ioFileStatus :
            myCurrentItem.getClient(itemFile).doStatus(itemFile, false);
        }
        catch (SVNException e) {
          LOG.info(e);
          statusInner = null;
        }
        if (statusInner == null)  return;

        final SVNStatusType status = statusInner.getNodeStatus();
        final VirtualFile vf = myCurrentItem.getPath().getVirtualFile();
        if (SVNStatusType.STATUS_IGNORED.equals(status)) {
          if (vf != null) {
            myReceiver.processIgnored(vf);
          }
          return;
        }
        if (SVNStatusType.STATUS_UNVERSIONED.equals(status) || SVNStatusType.UNKNOWN.equals(status)) {
          if (vf != null) {
            myReceiver.processUnversioned(vf);
            processRecursively(vf, myCurrentItem.getDepth());
          }
          return;
        }
        if (SVNStatusType.OBSTRUCTED.equals(status) || SVNStatusType.STATUS_NONE.equals(status)) {
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

    public void handleStatus(final SVNStatus status) throws SVNException {
      myPartner.checkCanceled();
      final File ioFile = status.getFile();
      checkIfCopyRootWasReported(status, ioFile);

      final VirtualFile vFile = getVirtualFile(ioFile);
      if (vFile != null) {
        final Boolean excluded = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            if (myProject.isDisposed()) return null;
            return myPartner.isExcluded(vFile);
          }
        });
        if (Boolean.TRUE.equals(excluded)) return;
      }
      if (myProject.isDisposed()) throw new ProcessCanceledException();

      if ((vFile != null) && (SvnVcs.svnStatusIsUnversioned(status))) {
        if (vFile.isDirectory()) {
          if (FileUtil.filesEqual(myCurrentItem.getPath().getIOFile(), ioFile)) {
            //myReceiver.processUnversioned(vFile);
            //processRecursively(vFile, myCurrentItem.getDepth());
          } else {
            final MyItem childItem = new MyItem(myVcs, new FilePathImpl(vFile), SVNDepth.INFINITY,
                                                myPartner.createStatusClient(), true);
            myQueue.add(childItem);
          }
        } else {
          myReceiver.processUnversioned(vFile);
        }
      } else {
        final FilePath path = VcsUtil.getFilePath(ioFile, status.getKind().equals(SVNNodeKind.DIR));
        myReceiver.process(path, status);
      }
    }
  }

  private VirtualFile getVirtualFile(File ioFile) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile vFile = lfs.findFileByIoFile(ioFile);
    if (vFile == null) {
      vFile = lfs.refreshAndFindFileByIoFile(ioFile);
    }
    return vFile;
  }
}
