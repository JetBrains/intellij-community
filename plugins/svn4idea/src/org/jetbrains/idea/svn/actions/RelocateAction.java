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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RelocateDialog;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;

/**
 * @author yole
 */
public class RelocateAction extends BasicAction {
  protected String getActionName(final AbstractVcs vcs) {
    return "Relocate working copy to a different URL";
  }

  protected boolean isEnabled(final Project project, final SvnVcs vcs, final VirtualFile file) {
    return SvnStatusUtil.isUnderControl(project, file);
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(final Project project, final SvnVcs activeVcs, final VirtualFile file, DataContext context) throws VcsException {
    Info info = activeVcs.getInfo(file);
    assert info != null;
    RelocateDialog dlg = new RelocateDialog(project, info.getURL());
    if (!dlg.showAndGet()) {
      return;
    }
    final String beforeURL = dlg.getBeforeURL();
    final String afterURL = dlg.getAfterURL();
    if (beforeURL.equals(afterURL)) return;
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setIndeterminate(true);
        }

        try {
          File path = new File(file.getPath());

          activeVcs.getFactory(path).createRelocateClient().relocate(path, beforeURL, afterURL);
          VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
        }
        catch (final VcsException e) {
          WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
            public void run() {
              Messages.showErrorDialog(project, "Error relocating working copy: " + e.getMessage(), "Relocate Working Copy");
            }
          }, null, project);
        }
      }
    }, "Relocating Working Copy", false, project);
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] file, DataContext context) throws VcsException {
  }

  protected boolean isBatchAction() {
    return false;
  }
}
