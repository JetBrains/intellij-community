/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.IdeBundle;


public class SynchronizeCurrentFileAction extends AnAction {

  public void update(AnActionEvent e) {
    final VirtualFile[] files = getFiles(e);
    if (files != null && files.length > 0) {
      e.getPresentation().setEnabled(true);
      if (files.length == 1) {
        e.getPresentation().setText(IdeBundle.message("action.synchronize.file", files[0].getName()));
      } else {
        e.getPresentation().setText(IdeBundle.message("action.synchronize.selected.files"));
      }
    } else {
      e.getPresentation().setEnabled(false);
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final VirtualFile[] files = getFiles(e);

    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        final int response = Messages.showYesNoDialog(getProject(e),
                                                      IdeBundle.message("prompt.recursively.synchronize.directory"),
                                                      IdeBundle.message("title.synchronize.files"),
                                                      Messages.getQuestionIcon());
        if (response == 1) {
          return;
        }
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (int i = 0; i < files.length; i++) {
          final VirtualFile file = files[i];
          file.refresh(false, true);
        }
      }
    });

    final FileStatusManager statusManager = FileStatusManager.getInstance(getProject(e));
    for (VirtualFile file : files) {
      statusManager.fileStatusChanged(file);
    }
  }

  private static Project getProject(AnActionEvent event) {
    return (Project)event.getDataContext().getData(DataConstants.PROJECT);
  }

  private static VirtualFile[] getFiles(final AnActionEvent e) {
    return (VirtualFile[])e.getDataContext().getData(DataConstants.VIRTUAL_FILE_ARRAY);
  }
}