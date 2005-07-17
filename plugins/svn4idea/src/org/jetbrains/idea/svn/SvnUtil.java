/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
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
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

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
          myLocations.add(info.getURL());
        }
      }
      catch (SVNException e) {
        //
      }
      return result;
    }
  }
}
