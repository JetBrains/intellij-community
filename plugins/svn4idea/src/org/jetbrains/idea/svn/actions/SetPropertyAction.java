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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.SetPropertyDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

public class SetPropertyAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return "Set Property";
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    try {
      SVNWCClient wcClient = vcs.createWCClient();
      SVNInfo info = wcClient.doInfo(new File(file.getPath()), SVNRevision.WORKING);
      return info != null && info.getURL() != null;
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

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] file, DataContext context, AbstractVcsHelper helper)
    throws VcsException {
    File[] ioFiles = new File[file.length];
    for (int i = 0; i < ioFiles.length; i++) {
      ioFiles[i] = new File(file[i].getPath());
    }

    SetPropertyDialog dialog = new SetPropertyDialog(project, ioFiles, null, true);
    dialog.show();

    if (dialog.isOK()) {
      String name = dialog.getPropertyName();
      String value = dialog.getPropertyValue();
      boolean recursive = dialog.isRecursive();

      SVNWCClient wcClient = new SVNWCClient(null, null);
      for (int i = 0; i < ioFiles.length; i++) {
        File ioFile = ioFiles[i];
        try {
          wcClient.doSetProperty(ioFile, name, value, false, recursive, null);
        }
        catch (SVNException e) {
          throw new VcsException(e);
        }
      }
    }
  }

  protected boolean isBatchAction() {
    return true;
  }
}
