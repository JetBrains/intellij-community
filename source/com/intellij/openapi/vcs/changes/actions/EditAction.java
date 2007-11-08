/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.12.2006
 * Time: 17:36:42
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class EditAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    List<VirtualFile> files = e.getData(ChangesListView.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    editFiles(project, files, exceptions);
    if (!exceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("edit.errors"));
    }
  }

  public static void editFiles(final Project project, final List<VirtualFile> files, final List<VcsException> exceptions) {
    ChangesUtil.processVirtualFilesByVcs(project, files, new ChangesUtil.PerVcsProcessor<VirtualFile>() {
      public void process(final AbstractVcs vcs, final List<VirtualFile> items) {
        final EditFileProvider provider = vcs.getEditFileProvider();
        if (provider != null) {
          try {
            provider.editFiles(items.toArray(new VirtualFile[items.size()]));
          }
          catch (VcsException e1) {
            exceptions.add(e1);
          }
          for(VirtualFile file: items) {
            VcsDirtyScopeManager.getInstance(project).fileDirty(file);
            FileStatusManager.getInstance(project).fileStatusChanged(file);
          }
        }
      }
    });
  }

  public void update(final AnActionEvent e) {
    List<VirtualFile> files = e.getData(ChangesListView.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }
}