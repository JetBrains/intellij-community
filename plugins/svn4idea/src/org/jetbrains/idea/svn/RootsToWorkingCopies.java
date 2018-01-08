// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

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
    myRechecker = () -> {
      final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs);
      synchronized (myLock) {
        clear();
        for (VirtualFile root : roots) {
          addRoot(root);
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
  public WorkingCopy getMatchingCopy(final Url url) {
    assert (! ApplicationManager.getApplication().isDispatchThread()) || ApplicationManager.getApplication().isUnitTestMode();
    if (url == null) return null;

    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(SvnVcs.getInstance(myProject));
    synchronized (myLock) {
      for (VirtualFile root : roots) {
        final WorkingCopy wcRoot = getWcRoot(root);
        if (wcRoot != null && (isAncestor(wcRoot.getUrl(), url) || isAncestor(url, wcRoot.getUrl()))) {
          return wcRoot;
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
    File workingCopyRoot = SvnUtil.getWorkingCopyRootNew(virtualToIoFile(root));
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
