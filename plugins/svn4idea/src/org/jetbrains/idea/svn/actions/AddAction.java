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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class AddAction extends BasicAction {
  static final Logger log = Logger.getInstance("org.jetbrains.idea.svn.action.AddAction");

  protected String getActionName(AbstractVcs vcs) {
    log.debug("enter: getActionName");
    return SvnBundle.message("action.name.add.files", vcs.getName());
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    SvnVcs.SVNStatusHolder cachedStatus = vcs.getCachedStatus(file);
    SVNStatus status;
    try {
      if (cachedStatus != null) {
        status = cachedStatus.getStatus();
      } else {
        SVNStatusClient stClient = new SVNStatusClient(null, null);
        status = stClient.doStatus(new File(file.getPath()), false);
        vcs.cacheStatus(file, status);
      }
      return status != null &&
             (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
              status.getContentsStatus() == SVNStatusType.STATUS_IGNORED);
    }
    catch (SVNException e) {
      //
    }
    return false;
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void batchPerform(final Project project, SvnVcs activeVcs, VirtualFile[] files, DataContext context, AbstractVcsHelper helper)
    throws VcsException {
    log.debug("enter: batchPerform");

    Collection exceptions = new ArrayList();
    SvnVcs vcs = SvnVcs.getInstance(project);
    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      try {
        SVNWCClient wcClient = vcs.createWCClient();
        wcClient.setEventHandler(new AddEventListener(project));
        wcClient.doAdd(new File(file.getPath()), false, false, true, true);
      }
      catch (SVNException e) {
        exceptions.add(e.getMessage());
      }
    }
    if (!exceptions.isEmpty()) {
      throw new VcsException(exceptions);
    }
  }

  protected boolean isBatchAction() {
    log.debug("enter: isBatchAction");
    return true;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context, AbstractVcsHelper helper)
    throws VcsException {
    try {
      SVNWCClient wcClient = activeVcs.createWCClient();
      wcClient.setEventHandler(new AddEventListener(project));
      wcClient.doAdd(new File(file.getPath()), false, false, true, true);
    }
    catch (SVNException e) {
      VcsException ve = new VcsException(e);
      ve.setVirtualFile(file);
      throw ve;
    }
  }

  private static class AddEventListener implements ISVNEventHandler {
    private final Project myProject;

    public AddEventListener(Project project) {
      myProject = project;
    }

    public void handleEvent(SVNEvent event, double progress) {
      if (event.getAction() == SVNEventAction.ADD && event.getFile() != null) {
        VirtualFile vfile = VirtualFileManager.getInstance()
          .findFileByUrl("file://" + event.getFile().getAbsolutePath().replace(File.separatorChar, '/'));
        if (vfile != null) {
          VcsDirtyScopeManager.getInstance(myProject).fileDirty(vfile);
        }
      }
    }

    public void checkCancelled() throws SVNCancelException {
    }
  }
}
