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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.Arrays;
import java.util.List;

public abstract class BasicAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.actions.BasicAction");

  public void actionPerformed(AnActionEvent event) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: actionPerformed(id='" + ActionManager.getInstance().getId(this) + "')");
    }
    final DataContext dataContext = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (LOG.isDebugEnabled() && files != null) {
      LOG.debug("files='" + Arrays.asList(files) + "'");
    }
    if (files == null || files.length == 0) return;

    final SvnVcs vcs = SvnVcs.getInstance(project);
    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, files)) {
      return;
    }

    if (project != null) {
      project.save();
    }

    final String actionName = getActionName(vcs);

    final AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
    LocalHistoryAction action = LocalHistoryAction.NULL;
    if (witeLocalHistory() && actionName != null) {
      action = LocalHistory.getInstance().startAction(actionName);
    }

    try {
      List<VcsException> exceptions = helper.runTransactionRunnable(vcs, new TransactionRunnable() {
        public void run(List<VcsException> exceptions) {
          VirtualFile badFile = null;
          try {
            if (isBatchAction()) {
              batchExecute(vcs, files, dataContext, helper);
            }
            else {
              for (int i = 0; files != null && i < files.length; i++) {
                VirtualFile file = files[i];
                badFile = file;
                execute(vcs, file, dataContext, helper);
              }
            }
          }
          catch (VcsException ex) {
            ex.setVirtualFile(badFile);
            exceptions.add(ex);
          }
        }
      }, null);

      helper.showErrors(exceptions, actionName != null ? actionName : vcs.getName());
    }
    finally {
      action.finish();
    }
  }

  protected boolean witeLocalHistory() {
    return true;
  }

  public void update(AnActionEvent e) {
    //LOG.debug("enter: update class:"+getClass().getName());
    super.update(e);

    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (files == null || files.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }

    SvnVcs vcs = SvnVcs.getInstance(project);
    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, files)) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }

    boolean enabled = true;
    if (!needsAllFiles()) {
      enabled = false;
    }

    LOG.debug(getClass().getName() + (enabled ? " needsAllFiles" : " needsSingleFile"));
    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      boolean fileEnabled = false;
      try {
        fileEnabled = isEnabled(vcs, file);
      }
      catch (Throwable t) {
        LOG.debug(t);

      }
      LOG.debug("file:" + file.getPath() + (fileEnabled ? " is enabled" : " is not enabled"));
      if (needsAllFiles()) {
        if (!fileEnabled) {
          LOG.debug("now disabled");
          enabled = false;
          break;
        }
      }
      else {
        if (fileEnabled) {
          LOG.debug("now enabled");
          enabled = true;
        }
      }
    }

    presentation.setEnabled(enabled);
    presentation.setVisible(true);
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected void execute(@NotNull SvnVcs vcs,
                         final VirtualFile file,
                         DataContext context,
                         AbstractVcsHelper helper) throws VcsException {
    if (file.isDirectory()) {
      perform(vcs, file, context);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          file.refresh(false, true);
        }
      });

      doVcsRefresh(vcs, file);
    }
    else {
      perform(vcs, file, context);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          file.refresh(false, true);
        }
      });
      doVcsRefresh(vcs, file);
    }
  }

  protected void doVcsRefresh(@NotNull SvnVcs vcs, final VirtualFile file) {
    VcsDirtyScopeManager.getInstance(vcs.getProject()).fileDirty(file);
  }

  private void batchExecute(@NotNull SvnVcs vcs,
                            final VirtualFile[] file,
                            DataContext context,
                            AbstractVcsHelper helper) throws VcsException {
    batchPerform(vcs, file, context);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (int i = 0; file != null && i < file.length; i++) {
          file[i].refresh(false, true);
        }
      }
    });

    for (int i = 0; file != null && i < file.length; i++) {
      doVcsRefresh(vcs, file[i]);
    }
  }

  protected abstract String getActionName(AbstractVcs vcs);

  protected abstract boolean isEnabled(@NotNull SvnVcs vcs, VirtualFile file);

  protected abstract void perform(@NotNull SvnVcs vcs, VirtualFile file, DataContext context) throws VcsException;

  protected abstract void batchPerform(@NotNull SvnVcs vcs, VirtualFile[] file, DataContext context) throws VcsException;

  protected abstract boolean isBatchAction();
}
