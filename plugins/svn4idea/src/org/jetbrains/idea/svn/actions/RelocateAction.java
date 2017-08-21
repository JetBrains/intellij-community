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

public class RelocateAction extends BasicAction {

  private static final Logger LOG = Logger.getInstance(RelocateAction.class);

  @NotNull
  @Override
  protected String getActionName() {
    return "Relocate working copy to a different URL";
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return SvnStatusUtil.isUnderControl(vcs, file);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    Info info = vcs.getInfo(file);
    if (info == null) {
      LOG.info("Could not get info for " + file);
      return;
    }

    RelocateDialog dlg = new RelocateDialog(vcs.getProject(), info.getURL());
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

        vcs.getFactory(path).createRelocateClient().relocate(path, beforeURL, afterURL);
        VcsDirtyScopeManager.getInstance(vcs.getProject()).markEverythingDirty();
      }
      catch (VcsException e) {
        runOrInvokeLaterAboveProgress(
          () -> Messages.showErrorDialog(vcs.getProject(), "Error relocating working copy: " + e.getMessage(), "Relocate Working Copy"),
          null, vcs.getProject());
      }
    }, "Relocating Working Copy", false, vcs.getProject());
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
