/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.util.ArrayUtil.isEmpty;

public abstract class BasicAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (isEmpty(files)) return;

    SvnVcs vcs = SvnVcs.getInstance(project);
    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, files)) return;

    project.save();

    String actionName = getActionName();
    LocalHistoryAction action = LocalHistory.getInstance().startAction(actionName);
    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);

    try {
      List<VcsException> exceptions = helper.runTransactionRunnable(vcs, exceptionList -> {
        VirtualFile badFile = null;
        try {
          if (isBatchAction()) {
            batchExecute(vcs, files, e.getDataContext());
          }
          else {
            for (VirtualFile file : files) {
              badFile = file;
              execute(vcs, file, e.getDataContext());
            }
          }
        }
        catch (VcsException ex) {
          ex.setVirtualFile(badFile);
          exceptionList.add(ex);
        }
      }, null);

      helper.showErrors(exceptions, actionName);
    }
    finally {
      action.finish();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    SvnVcs vcs = project != null ? SvnVcs.getInstance(project) : null;
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    boolean visible = project != null;

    e.getPresentation().setEnabled(visible && vcs != null && !isEmpty(files) && isEnabled(vcs, files));
    e.getPresentation().setVisible(visible);
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected void execute(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    perform(vcs, file, context);

    getApplication().runWriteAction(() -> file.refresh(false, true));
    doVcsRefresh(vcs, file);
  }

  protected void doVcsRefresh(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    VcsDirtyScopeManager.getInstance(vcs.getProject()).fileDirty(file);
  }

  private void batchExecute(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) throws VcsException {
    batchPerform(vcs, files, context);

    getApplication().runWriteAction(() -> {
      for (VirtualFile file : files) {
        file.refresh(false, true);
      }
    });
    for (VirtualFile file : files) {
      doVcsRefresh(vcs, file);
    }
  }

  @NotNull
  protected abstract String getActionName();

  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files) {
    Stream<VirtualFile> fileStream = Stream.of(files);
    Predicate<VirtualFile> enabledPredicate = file -> isEnabled(vcs, file);

    return ProjectLevelVcsManager.getInstance(vcs.getProject()).checkAllFilesAreUnder(vcs, files) &&
           (needsAllFiles() ? fileStream.allMatch(enabledPredicate) : fileStream.anyMatch(enabledPredicate));
  }

  protected abstract boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file);

  protected abstract void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException;

  protected abstract void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) throws VcsException;

  protected abstract boolean isBatchAction();
}
