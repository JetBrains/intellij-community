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
public abstract class SplitAction extends AnAction{
  private final int myOrientation;

  protected SplitAction(final int orientation){
    myOrientation = orientation;
  }

  public void actionPerformed(final AnActionEvent event) {
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    fileEditorManager.createSplitter (myOrientation);
  }

  public void update(final AnActionEvent event) {
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    final Presentation presentation = event.getPresentation();
    presentation.setText (myOrientation == SwingConstants.VERTICAL ? "Split _Vertically" : "Split Hori_zontally");
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    presentation.setEnabled(fileEditorManager.hasOpenedFile ());
  }
}
