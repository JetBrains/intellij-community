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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.CopyDialog;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class CopyAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("action.Subversion.Copy.text");
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    if (file == null) {
      return false;
    }
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

  protected void perform(final Project project, final SvnVcs activeVcs, VirtualFile file, DataContext context)
    throws VcsException {
    CopyDialog dialog = new CopyDialog(project, true, new File(file.getPath()));
    dialog.show();
    if (dialog.isOK()) {
      final String dstURL = dialog.getToURL();
      final SVNRevision revision = dialog.getRevision();
      final String comment = dialog.getComment();
      final Ref<Exception> exception = new Ref<Exception>();
      final boolean isSrcFile = dialog.isCopyFromWorkingCopy();
      final File srcFile = new File(dialog.getCopyFromPath());
      final SVNURL srcUrl;
      final SVNURL dstSvnUrl;
      final SVNURL parentUrl;
      try {
        final SVNWCClient wcClient = activeVcs.createWCClient();
        srcUrl = SVNURL.parseURIEncoded(dialog.getCopyFromUrl());
        dstSvnUrl = SVNURL.parseURIEncoded(dstURL);
        parentUrl = dstSvnUrl.removePathTail();

        if (!dirExists(parentUrl, wcClient)) {
          int rc = Messages.showYesNoDialog(project, "The repository path '" + parentUrl + "' does not exist. Would you like to create it?",
                                            "Branch or Tag", Messages.getQuestionIcon());
          if (rc == 1) {
            return;
          }
        }

      }
      catch (SVNException e) {
        throw new VcsException(e);
      }

      Runnable copyCommand = new Runnable() {
        public void run() {
          try {
            ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
            SVNCopyClient client = activeVcs.createCopyClient();
            if (progress != null) {
              progress.setText(SvnBundle.message("progress.text.copy.to", dstURL));
              client.setEventHandler(new CopyEventHandler(progress));
            }
            checkCreateDir(parentUrl, activeVcs, comment);
            SVNCommitInfo result;
            if (isSrcFile) {
              result = client.doCopy(srcFile, revision, dstSvnUrl, comment);
            }
            else {
              result = client.doCopy(srcUrl, revision, dstSvnUrl, false, comment);
            }
            if (result != null && result != SVNCommitInfo.NULL) {
              WindowManager.getInstance().getStatusBar(project)
                .setInfo(SvnBundle.message("status.text.comitted.revision", result.getNewRevision()));
            }
          }
          catch (Exception e) {
            exception.set(e);
          }
        }
      };
      ProgressManager.getInstance().runProcessWithProgressSynchronously(copyCommand, SvnBundle.message("progress.title.copy"), false, project);
      if (!exception.isNull()) {
        throw new VcsException(exception.get());
      }
    }
  }

  private static void checkCreateDir(SVNURL url, final SvnVcs activeVcs, final String comment) throws SVNException, VcsException {
    final SVNURL baseUrl = url;
    SVNWCClient client = activeVcs.createWCClient();
    List<SVNURL> dirsToCreate = new ArrayList<SVNURL>();
    while(!dirExists(url, client)) {
      dirsToCreate.add(0, url);
      url = url.removePathTail();
      if (url.getPath().length() == 0) {
        throw new VcsException("Invalid repository root path for " + baseUrl);
      }
    }
    SVNCommitClient commitClient = activeVcs.createCommitClient();
    commitClient.doMkDir(dirsToCreate.toArray(new SVNURL[dirsToCreate.size()]), comment);
  }

  private static boolean dirExists(final SVNURL url, final SVNWCClient client) throws SVNException {
    try {
      client.doInfo(url, SVNRevision.UNDEFINED, SVNRevision.HEAD);
    }
    catch(SVNException e) {
      if (e.getErrorMessage().getErrorCode().equals(SVNErrorCode.RA_ILLEGAL_URL)) {
        return false;
      }
      throw e;
    }
    return true;
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] files, DataContext context)
    throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }

  private static class CopyEventHandler implements ISVNEventHandler {
    private ProgressIndicator myProgress;

    public CopyEventHandler(ProgressIndicator progress) {
      myProgress = progress;
    }

    public void handleEvent(SVNEvent event, double p) {
      String path = event.getFile() != null ? event.getFile().getName() : event.getPath();
      if (path == null) {
        return;
      }
      if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
        myProgress.setText2(SvnBundle.message("progress.text2.adding", path));
      }
      else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
        myProgress.setText2(SvnBundle.message("progress.text2.deleting", path));
      }
      else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
        myProgress.setText2(SvnBundle.message("progress.text2.sending", path));
      }
      else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
        myProgress.setText2(SvnBundle.message("progress.text2.replacing", path));
      }
      else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
        myProgress.setText2(SvnBundle.message("progress.text2.transmitting.delta", path));
      }
    }

    public void checkCancelled() {
    }
  }
}
