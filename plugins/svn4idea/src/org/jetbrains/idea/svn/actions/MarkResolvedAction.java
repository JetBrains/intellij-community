/**
 * @copyright
 * ====================================================================
 * Copyright (c) 2003-2004 QintSoft.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://subversion.tigris.org/license-1.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 *
 * This software consists of voluntary contributions made by many
 * individuals.  For exact contribution history, see the revision
 * history and logs, available at http://svnup.tigris.org/.
 * ====================================================================
 * @endcopyright
 */
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.SelectFilesDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.Collection;
import java.util.TreeSet;

public class MarkResolvedAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return "Mark Resolved";
  }

  protected boolean needsAllFiles() {
    return false;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    if (file.isDirectory()) {
      SVNWCClient wcClient = new SVNWCClient(null, null);
      try {
        return wcClient.doInfo(new File(file.getPath()), SVNRevision.WORKING) != null;
      }
      catch (SVNException e) {
        //
      }
      return false;
    }
    SVNStatusClient stClient = new SVNStatusClient(null, null);
    try {
      SVNStatus status = stClient.doStatus(new File(file.getPath()), false);
      return status != null &&
             (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED ||
              status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED);
    }
    catch (SVNException e) {
      return false;
    }
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context, AbstractVcsHelper helper)
    throws VcsException {
    batchPerform(project, activeVcs, new VirtualFile[]{file}, context, helper);
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] files, DataContext context, AbstractVcsHelper helper)
    throws VcsException {
    SvnVcs vcs = SvnVcs.getInstance(project);
    ApplicationManager.getApplication().saveAll();
    Collection paths = collectResolvablePaths(files);
    if (paths.isEmpty()) {
      Messages.showInfoMessage(project, "No conflicts found", "No Conflicts");
      return;
    }
    String[] pathsArray = (String[])paths.toArray(new String[paths.size()]);
    SelectFilesDialog dialog = new SelectFilesDialog(project, "Select files and directories to mark resolved:", "Mark Resolved", "Mark Resolved", pathsArray);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    pathsArray = dialog.getSelectedPaths();
    try {
      SVNWCClient wcClient = vcs.createWCClient();
      for (int i = 0; i < pathsArray.length; i++) {
        String path = pathsArray[i];
        File ioFile = new File(path);
        wcClient.doResolve(ioFile, false);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
    finally {
      FileStatusManager.getInstance(project).fileStatusesChanged();
    }
  }

  protected boolean isBatchAction() {
    return true;
  }

  private static Collection collectResolvablePaths(VirtualFile[] files) {
    final Collection target = new TreeSet();
    SVNStatusClient stClient = new SVNStatusClient(null, null);
    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      try {
        stClient.doStatus(new File(file.getPath()), true, false, false, false, new ISVNStatusHandler() {
          public void handleStatus(SVNStatus status) {
            if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED ||
                status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
              target.add(status.getFile().getAbsolutePath());
            }
          }
        });
      }
      catch (SVNException e) {
        //
      }
    }
    return target;
  }
}
