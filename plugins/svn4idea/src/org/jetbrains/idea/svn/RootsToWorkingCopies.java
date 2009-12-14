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
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.util.*;

// 1. listen to roots changes
// 2. - possibly - to deletion/checkouts??? what if WC roots can be
public class RootsToWorkingCopies implements VcsListener {
  private final Object myLock;
  private final Map<VirtualFile, WorkingCopy> myRootMapping;
  private final Set<VirtualFile> myUnversioned;
  private final BackgroundTaskQueue myQueue;
  private final Project myProject;

  public RootsToWorkingCopies(final Project project) {
    myProject = project;
    myQueue = new BackgroundTaskQueue(project, "SVN VCS roots authorization checker");
    myLock = new Object();
    myRootMapping = new HashMap<VirtualFile, WorkingCopy>();
    myUnversioned = new HashSet<VirtualFile>();
  }

  public void addRoot(final VirtualFile root) {
    myQueue.run(new Task.Backgroundable(myProject, "Looking for '" + root.getPath() + "' working copy root", false,
                                        BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        calculateRoot(root);
      }
    });
  }

  @Nullable
  @CalledInBackground
  public WorkingCopy getMatchingCopy(final SVNURL url) {
    assert ! ApplicationManager.getApplication().isDispatchThread();

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
  public WorkingCopy getWcRoot(final VirtualFile root) {
    assert ! ApplicationManager.getApplication().isDispatchThread();

    synchronized (myLock) {
      if (myUnversioned.contains(root)) return null;
      final WorkingCopy existing = myRootMapping.get(root);
      if (existing != null) return existing;
    }
    return calculateRoot(root);
  }

  @Nullable
  private WorkingCopy calculateRoot(final VirtualFile root) {
    WorkingCopy workingCopy = null;
    try {
      final File workingCopyRoot = SVNWCUtil.getWorkingCopyRoot(new File(root.getPath()), true);
      SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
      try {
          wcAccess.probeOpen(workingCopyRoot, false, 0);
          SVNEntry entry = wcAccess.getVersionedEntry(workingCopyRoot, false);
          final SVNURL url = entry.getSVNURL();
          if (url != null) {
              workingCopy = new WorkingCopy(workingCopyRoot, url);
          }
      } finally {
          wcAccess.close();
      }
    }
    catch (SVNException e) {
      //
    }
    synchronized (myLock) {
      if (workingCopy == null) {
        myRootMapping.remove(root);
        myUnversioned.add(root);
      } else {
        myUnversioned.remove(root);
        myRootMapping.put(root, workingCopy);
      }
    }
    return workingCopy;
  }

  public void clear() {
    synchronized (myLock) {
      myRootMapping.clear();
      myUnversioned.clear();
    }
  }
  
  public void directoryMappingChanged() {
    final SvnVcs svnVcs = SvnVcs.getInstance(myProject);
    // todo +- here... shouldnt be
    svnVcs.getAuthNotifier().clear();
    
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(svnVcs);
    synchronized (myLock) {
      clear();
      for (VirtualFile root : roots) {
        addRoot(root);
      }
    }
  }
}
