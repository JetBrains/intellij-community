package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;

/**
 * @author Vladimir Kondratyev
 */
public final class UnsplitAllAction extends AnAction{
  public void actionPerformed(final AnActionEvent event) {
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    //VirtualFile file = fileEditorManager.getSelectedFiles()[0];
    fileEditorManager.unsplitAllWindow();
  }

  public void update(final AnActionEvent event) {
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    final Presentation presentation = event.getPresentation();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setText("U_nsplit All");
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    presentation.setEnabled (fileEditorManager.isInSplitter());
  }
}
