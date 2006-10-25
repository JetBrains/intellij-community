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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

public class CleanupAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("action.Subversion.Clenaup.text");
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    SVNInfo info;
    SvnVcs.SVNInfoHolder infoValue = vcs.getCachedInfo(file);
    if (infoValue != null) {
      info = infoValue.getInfo();
    } else {
      try {
        SVNWCClient wcClient = new SVNWCClient(vcs.getSvnAuthenticationManager(), vcs.getSvnOptions());
        info = wcClient.doInfo(new File(file.getPath()), SVNRevision.WORKING);
      }
      catch (SVNException e) {
        info = null;
      }
      vcs.cacheInfo(file, info);
    }
    return info != null && info.getURL() != null;
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context, AbstractVcsHelper helper)
    throws VcsException {
    SVNWCClient wcClient = new SVNWCClient(null, null);
    try {
      wcClient.doCleanup(new File(file.getPath()));
    }
    catch (SVNException e) {
      VcsException ve = new VcsException(e);
      ve.setVirtualFile(file);
      throw ve;
    }
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] file, DataContext context, AbstractVcsHelper helper)
    throws VcsException {
    throw new VcsException(SvnBundle.message("exception.text.cleanupaction.batchperform.not.implemented"));
  }

  protected boolean isBatchAction() {
    return false;
  }
}
