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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandLineStatusClient;
import org.jetbrains.idea.svn.portable.JavaHLSvnStatusClient;
import org.jetbrains.idea.svn.portable.SvnStatusClientI;
import org.jetbrains.idea.svn.portable.SvnkitSvnStatusClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.LinkedList;

public class SvnRecursiveStatusWalker {
  private final StatusWalkerPartner myPartner;
  private final Project myProject;
  private final StatusReceiver myReceiver;
  private final LinkedList<MyItem> myQueue;
  private final MyHandler myHandler;

  public SvnRecursiveStatusWalker(final Project project, final StatusReceiver receiver, final StatusWalkerPartner partner) {
    myProject = project;
    myReceiver = receiver;
    myPartner = partner;
    myQueue = new LinkedList<MyItem>();
    myHandler = new MyHandler();
  }

  public void go(final FilePath rootPath, final SVNDepth depth) throws SVNException {
    final MyItem root = new MyItem(myProject, rootPath, depth, myPartner.createStatusClient(), false);
    myQueue.add(root);

    while (! myQueue.isEmpty()) {
      myPartner.checkCanceled();

      final MyItem item = myQueue.removeFirst();
      final FilePath path = item.getPath();
      final File ioFile = path.getIOFile();

      if (path.isDirectory()) {
        myHandler.setCurrentItem(item);
        try {
          item.getClient(ioFile).doStatus(ioFile, SVNRevision.WORKING, item.getDepth(), false, false, true, false, myHandler, null);
          myHandler.checkIfCopyRootWasReported();
        }
        catch (SVNException e) {
          if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
            final VirtualFile virtualFile = path.getVirtualFile();
            if (virtualFile != null) {
              if (myPartner.isExcluded(virtualFile)) return;
              // self is unversioned
              myReceiver.processUnversioned(virtualFile);

              processRecursively(virtualFile, item.getDepth());
            }
          } else {
            throw e;
          }
        }
      } else {
        if (item.isIsInnerCopyRoot()) {
          // this means that the status of parent directory had already been checked and is unversioned;
          // to avoid SVN exception on status query to unversioned directory; and since we already know then that the file
          // status is "unversioned" -> just register the unversioned file
          if (path.getVirtualFile() != null) {
            myReceiver.processUnversioned(path.getVirtualFile());
          }
        } else {
          // just file
          final SVNStatus status = item.getClient().doStatus(ioFile, false, false);
          myReceiver.process(path, status);
        }
      }
    }
  }

  private static class MyItem {
    private final Project myProject;
    private final FilePath myPath;
    private final SVNDepth myDepth;
    private final SVNStatusClient myClient;
    private final SvnStatusClientI mySvnClient;
    private final boolean myIsInnerCopyRoot;
    private final SvnConfiguration myConfiguration17;

    private MyItem(Project project, FilePath path, SVNDepth depth, SVNStatusClient client, boolean isInnerCopyRoot) {
      myProject = project;
      myConfiguration17 = SvnConfiguration.getInstance(myProject);
      myPath = path;
      myDepth = depth;
      myClient = client;
      mySvnClient = new SvnkitSvnStatusClient(client);
      myIsInnerCopyRoot = isInnerCopyRoot;
    }

    public FilePath getPath() {
      return myPath;
    }

    public SVNDepth getDepth() {
      return myDepth;
    }

    public SvnStatusClientI getClient() {
      return mySvnClient;
    }
    
    public SvnStatusClientI getClient(final File file) {
      if (! SVNDepth.INFINITY.equals(myDepth)) {
        return mySvnClient;
      }
      // check format
      if (CheckJavaHL.isPresent() && SvnConfiguration.UseAcceleration.javaHL.equals(myConfiguration17.myUseAcceleration) &&
          Svn17Detector.is17(myProject, file)) {
        return new JavaHLSvnStatusClient(myProject);
      } else if (SvnConfiguration.UseAcceleration.commandLine.equals(myConfiguration17.myUseAcceleration) && Svn17Detector.is17(myProject, file)) {
        return new SvnCommandLineStatusClient(myProject);
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

    final SVNStatusClient childClient = myPartner.createStatusClient();
    final VirtualFile[] children = vFile.getChildren();
    for (VirtualFile child : children) {
      final FilePath filePath = VcsUtil.getFilePath(child.getPath(), child.isDirectory());
      // recursiveness is used ONLY for search of working copies that have unversioned files above
      final MyItem childItem = new MyItem(myProject, filePath, newDepth, childClient, true);
      myQueue.add(childItem);
    }
  }

  private class MyHandler implements ISVNStatusHandler {
    private MyItem myCurrentItem;
    private boolean myMetCurrentItem;

    public void setCurrentItem(MyItem currentItem) {
      myCurrentItem = currentItem;
      myMetCurrentItem = false;
    }

    public void checkIfCopyRootWasReported() {
      if (! myMetCurrentItem && myCurrentItem.isIsInnerCopyRoot()) {
        myMetCurrentItem = true;
        final SVNStatus statusInner = SvnUtil.getStatus(SvnVcs.getInstance(myProject), myCurrentItem.getPath().getIOFile());
        if (statusInner == null)  return;

        final SVNStatusType status = statusInner.getNodeStatus();
        if (SVNStatusType.OBSTRUCTED.equals(status) || SVNStatusType.STATUS_IGNORED.equals(status) ||
            SVNStatusType.STATUS_NONE.equals(status) || SVNStatusType.STATUS_UNVERSIONED.equals(status) ||
            SVNStatusType.UNKNOWN.equals(status)) {
          return;
        }
        if (myCurrentItem.getPath().getVirtualFile() != null) {
          myReceiver.processCopyRoot(myCurrentItem.getPath().getVirtualFile(), statusInner.getURL(),
                                     WorkingCopyFormat.getInstance(statusInner.getWorkingCopyFormat()));
        }
      }
    }

    public void handleStatus(final SVNStatus status) throws SVNException {
      myPartner.checkCanceled();
      final File ioFile = status.getFile();
      checkIfCopyRootWasReported();

      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile vFile = lfs.findFileByIoFile(ioFile);
      if (vFile == null) {
        vFile = lfs.refreshAndFindFileByIoFile(ioFile);
      }
      if ((vFile != null) && myPartner.isExcluded(vFile)) return;

      if ((vFile != null) && (SvnVcs.svnStatusIsUnversioned(status))) {
        myReceiver.processUnversioned(vFile);
        if (vFile.isDirectory()) {
          processRecursively(vFile, myCurrentItem.getDepth());
        }
      } else {
        final FilePath path = VcsUtil.getFilePath(ioFile, status.getKind().equals(SVNNodeKind.DIR));
        myReceiver.process(path, status);
      }
    }
  }
}
