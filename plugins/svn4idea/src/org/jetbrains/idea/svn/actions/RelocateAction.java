// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RelocateDialog;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;

import static com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class RelocateAction extends BasicAction {

  private static final Logger LOG = Logger.getInstance(RelocateAction.class);

  @NotNull
  @Override
  protected String getActionName() {
    return message("action.Subversion.Relocate.description");
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return SvnStatusUtil.isUnderControl(vcs, file);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) {
    Info info = vcs.getInfo(file);
    if (info == null) {
      LOG.info("Could not get info for " + file);
      return;
    }

    RelocateDialog dlg = new RelocateDialog(vcs.getProject(), info.getUrl());
    if (!dlg.showAndGet()) {
      return;
    }
    String beforeURL = dlg.getBeforeURL();
    String afterURL = dlg.getAfterURL();
    if (beforeURL.equals(afterURL)) return;
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setIndeterminate(true);
      }

      try {
        File path = VfsUtilCore.virtualToIoFile(file);

        vcs.getFactory(path).createRelocateClient().relocate(path, createUrl(beforeURL, false), createUrl(afterURL, false));
        VcsDirtyScopeManager.getInstance(vcs.getProject()).markEverythingDirty();
      }
      catch (VcsException e) {
        runOrInvokeLaterAboveProgress(
          () -> Messages.showErrorDialog(
            vcs.getProject(),
            message("dialog.message.error.relocating.working.copy", e.getMessage()),
            message("dialog.title.relocate.working.copy")
          ),
          null,
          vcs.getProject()
        );
      }
    }, message("progress.title.relocating.working.copy"), false, vcs.getProject());
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, VirtualFile @NotNull [] files, @NotNull DataContext context) {
  }

  @Override
  protected boolean isBatchAction() {
    return false;
  }
}
