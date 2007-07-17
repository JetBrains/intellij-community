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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.dialogs.LockDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class SvnUtil {
  @NonNls public static final String SVN_ADMIN_DIR_NAME = ".svn";
  @NonNls public static final String ENTRIES_FILE_NAME = "entries";
  @NonNls public static final String PATH_TO_LOCK_FILE = ".svn/lock";
  @NonNls public static final String LOCK_FILE_NAME = "lock";

  private SvnUtil() {
  }

  public static Collection<File> crawlWCRoots(File path, SvnWCRootCrawler callback, ProgressIndicator progress) {
    final Collection<File> result = new HashSet<File>();
    File parent = path.isFile() || !path.exists() ? path.getParentFile() : path;
    if (SVNWCUtil.isVersionedDirectory(parent)) {
      if (progress != null && progress.isCanceled()) {
        throw new ProcessCanceledException();
      }
      final Collection<File> pending = callback.handleWorkingCopyRoot(path, progress);
      if (progress != null && progress.isCanceled()) {
        throw new ProcessCanceledException();
      }
      for (final File aPending : pending) {
        result.addAll(crawlWCRoots(aPending, callback, progress));
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
          result.addAll(crawlWCRoots(child, callback, progress));
        }
      }
    }
    return result;
  }

  public static String[] getLocationsForModule(final SvnVcs vcs, File path, ProgressIndicator progress) {
    LocationsCrawler crawler = new LocationsCrawler(vcs);
    crawlWCRoots(path, crawler, progress);
    return crawler.getLocations();
  }

  public static void doLockFiles(Project project, final SvnVcs activeVcs, final File[] ioFiles) throws VcsException {
    final String lockMessage;
    final boolean force;
    // TODO[yole]: check for shift pressed
    if (activeVcs.getCheckoutOptions().getValue()) {
      LockDialog dialog = new LockDialog(project, true, ioFiles != null && ioFiles.length > 1);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      lockMessage = dialog.getComment();
      force = dialog.isForce();
    }
    else {
      lockMessage = "";
      force = false;
    }

    final SVNException[] exception = new SVNException[1];
    final Collection<String> failedLocks = new ArrayList<String>();
    final int[] count = new int[]{ioFiles.length};
    final ISVNEventHandler eventHandler = new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) {
        if (event.getAction() == SVNEventAction.LOCK_FAILED) {
          failedLocks.add(event.getErrorMessage() != null ?
                          event.getErrorMessage().getFullMessage() :
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
            progress.setText(SvnBundle.message("progress.text.locking.files"));
          }
          for (File ioFile : ioFiles) {
            if (progress != null) {
              progress.checkCanceled();
            }
            File file = ioFile;
            if (progress != null) {
              progress.setText2(SvnBundle.message("progress.text2.processing.file", file.getName()));
            }
            wcClient.doLock(new File[]{file}, force, lockMessage);
          }
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.lock.files"), false, project);
    if (!failedLocks.isEmpty()) {
      String[] failedFiles = failedLocks.toArray(new String[failedLocks.size()]);
      List<VcsException> exceptions = new ArrayList<VcsException>();

      for (String file : failedFiles) {
        exceptions.add(new VcsException(SvnBundle.message("exception.text.locking.file.failed", file)));
      }
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, SvnBundle.message("message.title.lock.failures"));
    }

    WindowManager.getInstance().getStatusBar(project).setInfo(SvnBundle.message("message.text.files.locked", count[0]));
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
  }

  public static void doUnlockFiles(Project project, final SvnVcs activeVcs, final File[] ioFiles) throws VcsException {
    final boolean force = true;
    final SVNException[] exception = new SVNException[1];
    final Collection<String> failedUnlocks = new ArrayList<String>();
    final int[] count = new int[]{ioFiles.length};
    final ISVNEventHandler eventHandler = new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) {
        if (event.getAction() == SVNEventAction.UNLOCK_FAILED) {
          failedUnlocks.add(event.getErrorMessage() != null ?
                            event.getErrorMessage().getFullMessage() :
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
            progress.setText(SvnBundle.message("progress.text.unlocking.files"));
          }
          for (File ioFile : ioFiles) {
            if (progress != null) {
              progress.checkCanceled();
            }
            File file = ioFile;
            if (progress != null) {
              progress.setText2(SvnBundle.message("progress.text2.processing.file", file.getName()));
            }
            wcClient.doUnlock(new File[]{file}, force);
          }
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.unlock.files"), false, project);
    if (!failedUnlocks.isEmpty()) {
      String[] failedFiles = failedUnlocks.toArray(new String[failedUnlocks.size()]);
      List<VcsException> exceptions = new ArrayList<VcsException>();

      for (String file : failedFiles) {
        exceptions.add(new VcsException(SvnBundle.message("exception.text.failed.to.unlock.file", file)));
      }
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, SvnBundle.message("message.title.unlock.failures"));
    }

    WindowManager.getInstance().getStatusBar(project).setInfo(SvnBundle.message("message.text.files.unlocked", count[0]));
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
  }

  private static class LocationsCrawler implements SvnWCRootCrawler {
    private SvnVcs myVcs;
    private Collection<String> myLocations;

    public LocationsCrawler(SvnVcs vcs) {
      myVcs = vcs;
      myLocations = new ArrayList<String>();
    }

    public String[] getLocations() {
      return myLocations.toArray(new String[myLocations.size()]);
    }

    public Collection<File> handleWorkingCopyRoot(File root, ProgressIndicator progress) {
      final Collection<File> result = new HashSet<File>();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.discovering.location", root.getAbsolutePath()));
      }
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
