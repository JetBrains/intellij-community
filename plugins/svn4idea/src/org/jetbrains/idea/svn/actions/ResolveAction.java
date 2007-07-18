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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResolveAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("action.name.resolve.conflict");
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    if (file.isDirectory()) return true;
    SVNStatus status;
    try {
      SvnVcs.SVNStatusHolder statusValue = vcs.getCachedStatus(file);
      if (statusValue != null) {
        status = statusValue.getStatus();
      } else {
        SVNStatusClient stClient = vcs.createStatusClient();
        status = stClient.doStatus(new File(file.getPath()), false);
      }
      if (status != null && status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED) {
        SVNInfo info;
        SvnVcs.SVNInfoHolder infoValue = vcs.getCachedInfo(file);
        if (infoValue != null) {
          info = infoValue.getInfo();
        } else {
          SVNWCClient wcClient = vcs.createWCClient();
          info = wcClient.doInfo(new File(file.getPath()), SVNRevision.WORKING);
          vcs.cacheInfo(file, info);
        }
        return info != null && info.getConflictNewFile() != null &&
               info.getConflictOldFile() != null &&
               info.getConflictWrkFile() != null;
      }
    }
    catch (SVNException e) {
      //
    }
    return false;
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context) throws VcsException {
    batchPerform(project, activeVcs, new VirtualFile[]{file}, context);
  }

  protected void batchPerform(final Project project, final SvnVcs activeVcs, final VirtualFile[] files, DataContext context) throws VcsException {
    boolean hasDirs = false;
    for(VirtualFile file: files) {
      if (file.isDirectory()) {
        hasDirs = true;
      }
    }
    final List<VirtualFile> fileList = new ArrayList<VirtualFile>();
    if (!hasDirs) {
      Collections.addAll(fileList, files);
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          for (VirtualFile file: files) {
            if (file.isDirectory()) {
              ProjectRootManager.getInstance(project).getFileIndex().iterateContentUnderDirectory(file, new ContentIterator() {
                public boolean processFile(final VirtualFile fileOrDir) {
                  ProgressManager.getInstance().checkCanceled();
                  if (!fileOrDir.isDirectory() && isEnabled(project, activeVcs, fileOrDir) && !fileList.contains(fileOrDir)) {
                    fileList.add(fileOrDir);
                  }
                  return true;
                }
              });
            }
            else {
              if (!fileList.contains(file)) {
                fileList.add(file);
              }
            }
          }
        }
      }, SvnBundle.message("progress.searching.for.files.with.conflicts"), true, project);
    }
    AbstractVcsHelper.getInstance(project).showMergeDialog(fileList, new SvnMergeProvider(project));
  }

  protected boolean isBatchAction() {
    return true;
  }
}
