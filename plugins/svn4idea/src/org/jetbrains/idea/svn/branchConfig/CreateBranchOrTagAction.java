/*
 * Copyright 2000-2014 JetBrains s.r.o.
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


package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.BasicAction;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.jetbrains.idea.svn.checkin.IdeaCommitHandler;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.update.AutoSvnUpdater;
import org.jetbrains.idea.svn.update.SingleRootSwitcher;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public class CreateBranchOrTagAction extends BasicAction {
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
    return SvnStatusUtil.isUnderControl(project, file);
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(final Project project, final SvnVcs activeVcs, VirtualFile file, DataContext context)
    throws VcsException {
    CreateBranchOrTagDialog dialog = new CreateBranchOrTagDialog(project, true, new File(file.getPath()));
    if (dialog.showAndGet()) {
      final String dstURL = dialog.getToURL();
      final SVNRevision revision = dialog.getRevision();
      final String comment = dialog.getComment();
      final Ref<Exception> exception = new Ref<>();
      final boolean isSrcFile = dialog.isCopyFromWorkingCopy();
      final File srcFile = new File(dialog.getCopyFromPath());
      final SVNURL srcUrl;
      final SVNURL dstSvnUrl;
      final SVNURL parentUrl;
      try {
        srcUrl = SVNURL.parseURIEncoded(dialog.getCopyFromUrl());
        dstSvnUrl = SVNURL.parseURIEncoded(dstURL);
        parentUrl = dstSvnUrl.removePathTail();
      }
      catch (SVNException e) {
        throw new SvnBindException(e);
      }

      if (!dirExists(activeVcs, parentUrl)) {
        int rc = Messages.showYesNoDialog(project, "The repository path '" + parentUrl + "' does not exist. Would you like to create it?",
                                          "Branch or Tag", Messages.getQuestionIcon());
        if (rc == Messages.NO) {
          return;
        }
      }

      Runnable copyCommand = new Runnable() {
        public void run() {
          try {
            ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
            CommitEventHandler handler = null;
            if (progress != null) {
              progress.setText(SvnBundle.message("progress.text.copy.to", dstURL));
              handler = new IdeaCommitHandler(progress);
            }

            SvnTarget source = isSrcFile ? SvnTarget.fromFile(srcFile, revision) : SvnTarget.fromURL(srcUrl, revision);
            long newRevision = activeVcs.getFactory(source).createCopyMoveClient()
              .copy(source, SvnTarget.fromURL(dstSvnUrl), revision, true, false, comment, handler);

            updateStatusBar(newRevision, project);
          }
          catch (Exception e) {
            exception.set(e);
          }
        }
      };
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(copyCommand, SvnBundle.message("progress.title.copy"), false, project);
      if (!exception.isNull()) {
        throw new VcsException(exception.get());
      }

      if (dialog.isCopyFromWorkingCopy() && dialog.isSwitchOnCreate()) {
        SingleRootSwitcher switcher = new SingleRootSwitcher(project, VcsUtil.getFilePath(srcFile, srcFile.isDirectory()), dstSvnUrl);
        AutoSvnUpdater.run(switcher, SvnBundle.message("action.name.switch"));
      }
      ;
    }
  }

  private void updateStatusBar(long revision, Project project) {
    if (revision > 0) {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);

      if (statusBar != null) {
        statusBar.setInfo(SvnBundle.message("status.text.comitted.revision", revision));
      }
    }
  }

  private static boolean dirExists(@NotNull final SvnVcs vcs, @NotNull final SVNURL url) throws SvnBindException {
    final Ref<SvnBindException> excRef = new Ref<>();
    final Ref<Boolean> resultRef = new Ref<>(Boolean.TRUE);

    final Runnable taskImpl = new Runnable() {
      public void run() {
        try {
          vcs.getInfo(url, SVNRevision.HEAD);
        }
        catch (SvnBindException e) {
          if (e.contains(SVNErrorCode.RA_ILLEGAL_URL)) {
            resultRef.set(Boolean.FALSE);
          }
          else {
            excRef.set(e);
          }
        }
      }
    };

    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(taskImpl, "Checking target folder", true, vcs.getProject());
    }
    else {
      taskImpl.run();
    }
    if (!excRef.isNull()) throw excRef.get();
    return resultRef.get();
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] files, DataContext context)
    throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
