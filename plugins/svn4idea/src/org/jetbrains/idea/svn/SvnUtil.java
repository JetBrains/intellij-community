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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.idea.svn.dialogs.LockDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.*;

public class SvnUtil {
  public static Collection crawlWCRoots(SvnVcs vcs, File path, SvnWCRootCrawler callback, ProgressIndicator progress) {
    final Collection result = new HashSet();
    File parent = path.isFile() || !path.exists() ? path.getParentFile() : path;
    if (SVNWCUtil.isVersionedDirectory(parent)) {
      if (progress != null && progress.isCanceled()) {
        throw new ProcessCanceledException();
      }
      final Collection pending = callback.handleWorkingCopyRoot(path, progress);
      if (progress != null && progress.isCanceled()) {
        throw new ProcessCanceledException();
      }
      for (Iterator pendingFiles = pending.iterator(); pendingFiles.hasNext();) {
        File file = (File)pendingFiles.next();
        result.addAll(crawlWCRoots(vcs, file, callback, progress));
      }
      result.add(path);
    }
    else if (path.isDirectory()) {
      if (progress != null && progress.isCanceled()) {
        throw new ProcessCanceledException();
      }
      File[] children = path.listFiles();
      for (int i = 0; children != null && i < children.length; i++) {
        if (progress != null && progress.isCanceled()) {
          throw new ProcessCanceledException();
        }
        File child = children[i];
        if (child.isDirectory()) {
          result.addAll(crawlWCRoots(vcs, child, callback, progress));
        }
      }
    }
    return result;
  }

  public static String[] getLocationsForModule(final SvnVcs vcs, File path, ProgressIndicator progress) {
    LocationsCrawler crawler = new LocationsCrawler(vcs, "Discovering location for");
    crawlWCRoots(vcs, path, crawler, progress);
    return crawler.getLocations();
  }

  public static void doLockFiles(Project project, final SvnVcs activeVcs, final File[] ioFiles,
                                 AbstractVcsHelper helper) throws VcsException {
    LockDialog dialog = new LockDialog(project, true);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    final String lockMessage = dialog.getComment();
    final boolean force = dialog.isForce();
    final SVNException[] exception = new SVNException[1];
    final Collection failedLocks = new ArrayList();
    final int[] count = new int[]{ioFiles.length};
    final ISVNEventHandler eventHandler = new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) {
        if (event.getAction() == SVNEventAction.LOCK_FAILED) {
          failedLocks.add(event.getErrorMessage() != null ?
                          event.getErrorMessage() :
                          event.getFile().getAbsolutePath());
          count[0]--;
        }
      }

      public void checkCancelled() {
      }
    };

    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        SVNWCClient wcClient;

        try {
          wcClient = activeVcs.createWCClient();
          wcClient.setEventHandler(eventHandler);
          if (progress != null) {
            progress.setText("Locking files in repository...");
          }
          for (int i = 0; i < ioFiles.length; i++) {
            if (progress != null) {
              progress.checkCanceled();
            }
            File file = ioFiles[i];
            if (progress != null) {
              progress.setText2("Processing file " + file.getName());
            }
            wcClient.doLock(new File[]{file}, force, lockMessage);
          }
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };

    ApplicationManager.getApplication().runProcessWithProgressSynchronously(command,
                                                                            "Lock Files", false, project);
    if (!failedLocks.isEmpty() && helper != null) {
      String[] failedFiles = (String[])failedLocks.toArray(new String[failedLocks.size()]);
      List exceptions = new ArrayList();

      for (int i = 0; i < failedFiles.length; i++) {
        String file = failedFiles[i];
        exceptions.add(new VcsException("Failed to lock file: " + file));
      }
      helper.showErrors(exceptions, "Lock Failures");
    }

    String message = "Failed to lock files";
    if (count[0] > 0 && exception[0] == null) {
      message = count[0] == 1 ? "1 file locked" : count[0] + " files locked";
    }
    WindowManager.getInstance().getStatusBar(project).setInfo(message);
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
  }

  public static void doUnlockFiles(Project project, final SvnVcs activeVcs, final File[] ioFiles,
                                   AbstractVcsHelper helper) throws VcsException {
    final boolean force = true;
    final SVNException[] exception = new SVNException[1];
    final Collection failedUnlocks = new ArrayList();
    final int[] count = new int[]{ioFiles.length};
    final ISVNEventHandler eventHandler = new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) {
        if (event.getAction() == SVNEventAction.UNLOCK_FAILED) {
          failedUnlocks.add(event.getErrorMessage() != null ?
                            event.getErrorMessage() :
                            event.getFile().getAbsolutePath());
          count[0]--;
        }
      }

      public void checkCancelled() {
      }
    };

    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        SVNWCClient wcClient;

        try {
          wcClient = activeVcs.createWCClient();
          wcClient.setEventHandler(eventHandler);
          if (progress != null) {
            progress.setText("Unlocking files in repository...");
          }
          for (int i = 0; i < ioFiles.length; i++) {
            if (progress != null) {
              progress.checkCanceled();
            }
            File file = ioFiles[i];
            if (progress != null) {
              progress.setText2("Processing file " + file.getName());
            }
            wcClient.doUnlock(new File[]{file}, force);
          }
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };

    ApplicationManager.getApplication().runProcessWithProgressSynchronously(command,
                                                                            "Unlock Files", false, project);
    if (!failedUnlocks.isEmpty() && helper != null) {
      String[] failedFiles = (String[])failedUnlocks.toArray(new String[failedUnlocks.size()]);
      List exceptions = new ArrayList();

      for (int i = 0; i < failedFiles.length; i++) {
        String file = failedFiles[i];
        exceptions.add(new VcsException("Failed to unlock file: " + file));
      }
      helper.showErrors(exceptions, "Unlock Failures");
    }

    String message = "Failed to unlock files";
    if (count[0] > 0 && exception[0] == null) {
      message = count[0] == 1 ? "1 file unlocked" : count[0] + " files unlocked";
    }
    WindowManager.getInstance().getStatusBar(project).setInfo(message);
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
  }

  private static class LocationsCrawler implements SvnWCRootCrawler {
    private SvnVcs myVcs;
    private String myProgressPrefix;
    private Collection myLocations;

    public LocationsCrawler(SvnVcs vcs, String progressPrefix) {
      myVcs = vcs;
      myProgressPrefix = progressPrefix;
      myLocations = new ArrayList();
    }

    public String[] getLocations() {
      return (String[])myLocations.toArray(new String[myLocations.size()]);
    }

    public Collection handleWorkingCopyRoot(File root, ProgressIndicator progress) {
      final Collection result = new HashSet();
      progress.setText(myProgressPrefix + " '" + root.getAbsolutePath() + "'");
      try {
        SVNWCClient wcClient = myVcs.createWCClient();
        SVNInfo info = wcClient.doInfo(root, SVNRevision.WORKING);
        if (info != null && info.getURL() != null) {
          myLocations.add(info.getURL().toString());
        }
      }
      catch (SVNException e) {
        //
      }
      return result;
    }
  }
}
