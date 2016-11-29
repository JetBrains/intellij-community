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
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// 1. listen to roots changes
// 2. - possibly - to deletion/checkouts??? what if WC roots can be
public class RootsToWorkingCopies implements VcsListener {
  private final Object myLock;
  private final Map<VirtualFile, WorkingCopy> myRootMapping;
  private final Set<VirtualFile> myUnversioned;
  private final BackgroundTaskQueue myQueue;
  private final Project myProject;
  private final ZipperUpdater myZipperUpdater;
  private Runnable myRechecker;
  private final SvnVcs myVcs;

  public RootsToWorkingCopies(final SvnVcs vcs) {
    myProject = vcs.getProject();
    myQueue = new BackgroundTaskQueue(myProject, "SVN VCS roots authorization checker");
    myLock = new Object();
    myRootMapping = new HashMap<>();
    myUnversioned = new HashSet<>();
    myVcs = vcs;
    myRechecker = new Runnable() {
      public void run() {
        final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs);
        synchronized (myLock) {
          clear();
          for (VirtualFile root : roots) {
            addRoot(root);
          }
        }
      }
    };
    myZipperUpdater = new ZipperUpdater(200, Alarm.ThreadToUse.POOLED_THREAD, myProject);
  }

  private void addRoot(final VirtualFile root) {
    myQueue.run(new Task.Backgroundable(myProject, "Looking for '" + root.getPath() + "' working copy root", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        calculateRoot(root);
      }
    });
  }

  @Nullable
  @CalledInBackground
  public WorkingCopy getMatchingCopy(final SVNURL url) {
    assert (! ApplicationManager.getApplication().isDispatchThread()) || ApplicationManager.getApplication().isUnitTestMode();
    if (url == null) return null;

    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(SvnVcs.getInstance(myProject));
    synchronized (myLock) {
      for (VirtualFile root : roots) {
        final WorkingCopy wcRoot = getWcRoot(root);
        if (wcRoot != null) {
          final SVNURL common = SVNURLUtil.getCommonURLAncestor(wcRoot.getUrl(), url);
          if (wcRoot.getUrl().equals(common) || url.equals(common)) {
            return wcRoot;
          }
        }
      }
    }
    return null;
  }

  @CalledInBackground
  @Nullable
  public WorkingCopy getWcRoot(@NotNull VirtualFile root) {
    assert (! ApplicationManager.getApplication().isDispatchThread()) || ApplicationManager.getApplication().isUnitTestMode();

    synchronized (myLock) {
      if (myUnversioned.contains(root)) return null;
      final WorkingCopy existing = myRootMapping.get(root);
      if (existing != null) return existing;
    }
    return calculateRoot(root);
  }

  @Nullable
  private WorkingCopy calculateRoot(@NotNull VirtualFile root) {
    File workingCopyRoot = SvnUtil.getWorkingCopyRootNew(new File(root.getPath()));
    WorkingCopy workingCopy = null;

    if (workingCopyRoot != null) {
      final Info svnInfo = myVcs.getInfo(workingCopyRoot);

      if (svnInfo != null && svnInfo.getURL() != null) {
        workingCopy = new WorkingCopy(workingCopyRoot, svnInfo.getURL(), true);
      }
    }

    return registerWorkingCopy(root, workingCopy);
  }

  private WorkingCopy registerWorkingCopy(@NotNull VirtualFile root, @Nullable WorkingCopy resolvedWorkingCopy) {
    synchronized (myLock) {
      if (resolvedWorkingCopy == null) {
        myRootMapping.remove(root);
        myUnversioned.add(root);
      } else {
        myUnversioned.remove(root);
        myRootMapping.put(root, resolvedWorkingCopy);
      }
    }
    return resolvedWorkingCopy;
  }

  public void clear() {
    synchronized (myLock) {
      myRootMapping.clear();
      myUnversioned.clear();
      myZipperUpdater.stop();
    }
  }
  
  public void directoryMappingChanged() {
    // todo +- here... shouldnt be
    myVcs.getAuthNotifier().clear();

    myZipperUpdater.queue(myRechecker);
  }
}
