package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * @author Vladimir Kondratyev
 */
public final class ChangeSplitterOrientationAction extends AnAction{
  public void actionPerformed(final AnActionEvent event) {
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    fileEditorManager.changeSplitterOrientation ();
  }

  public void update(final AnActionEvent event) {
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    final Presentation presentation = event.getPresentation();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setText("Change Splitter Orientations");
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    presentation.setEnabled (fileEditorManager.isInSplitter());
  }
}
