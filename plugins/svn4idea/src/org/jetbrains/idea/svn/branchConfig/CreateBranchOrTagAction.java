// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.BasicAction;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.jetbrains.idea.svn.checkin.IdeaCommitHandler;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.update.AutoSvnUpdater;
import org.jetbrains.idea.svn.update.SingleRootSwitcher;

import java.io.File;
import java.util.Objects;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.ui.Messages.showYesNoDialog;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.removePathTail;

public class CreateBranchOrTagAction extends BasicAction {
  @NotNull
  @Override
  protected String getActionName() {
    return message("action.Subversion.Copy.text");
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return SvnStatusUtil.isUnderControl(vcs, file);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    CreateBranchOrTagDialog dialog = new CreateBranchOrTagDialog(vcs, virtualToIoFile(file));
    if (dialog.showAndGet()) {
      Target source = Objects.requireNonNull(dialog.getSource());
      File sourceFile = dialog.getSourceFile();
      Url destination = Objects.requireNonNull(dialog.getDestination());
      Revision revision = dialog.getRevision();
      String comment = dialog.getComment();
      Url parentUrl = removePathTail(destination);

      if (!dirExists(vcs, parentUrl)) {
        int rc = showYesNoDialog(
          vcs.getProject(),
          message("dialog.message.repository.path.does.not.exist", parentUrl),
          message("copy.dialog.title"),
          getQuestionIcon()
        );
        if (rc == Messages.NO) {
          return;
        }
      }

      Ref<Exception> exception = new Ref<>();
      Runnable copyCommand = () -> {
        try {
          ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
          CommitEventHandler handler = null;
          if (progress != null) {
            progress.setText(message("progress.text.copy.to", destination.toDecodedString()));
            handler = new IdeaCommitHandler(progress);
          }

          long newRevision =
            vcs.getFactory(source).createCopyMoveClient().copy(source, Target.on(destination), revision, true, false, comment, handler);

          updateStatusBar(newRevision, vcs.getProject());
        }
        catch (Exception e) {
          exception.set(e);
        }
      };
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(copyCommand, message("progress.title.copy"), false, vcs.getProject());
      if (!exception.isNull()) {
        throw new VcsException(exception.get());
      }

      if (dialog.isSwitchOnCreate()) {
        SingleRootSwitcher switcher =
          new SingleRootSwitcher(vcs.getProject(), VcsUtil.getFilePath(sourceFile, sourceFile.isDirectory()), destination);
        AutoSvnUpdater.run(switcher, message("action.name.switch"));
      }
    }
  }

  private static void updateStatusBar(long revision, @NotNull Project project) {
    if (revision > 0) {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);

      if (statusBar != null) {
        statusBar.setInfo(message("status.text.committed.revision", revision));
      }
    }
  }

  private static boolean dirExists(@NotNull SvnVcs vcs, @NotNull Url url) throws SvnBindException {
    Ref<SvnBindException> excRef = new Ref<>();
    Ref<Boolean> resultRef = new Ref<>(Boolean.TRUE);
    Runnable taskImpl = () -> {
      try {
        vcs.getInfo(url, Revision.HEAD);
      }
      catch (SvnBindException e) {
        if (e.contains(ErrorCode.RA_ILLEGAL_URL)) {
          resultRef.set(Boolean.FALSE);
        }
        else {
          excRef.set(e);
        }
      }
    };

    if (getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        taskImpl,
        message("progress.title.checking.target.folder"),
        true,
        vcs.getProject()
      );
    }
    else {
      taskImpl.run();
    }
    if (!excRef.isNull()) throw excRef.get();
    return resultRef.get();
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, VirtualFile @NotNull [] files, @NotNull DataContext context) {
  }

  @Override
  protected boolean isBatchAction() {
    return false;
  }
}
