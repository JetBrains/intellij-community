/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public class GotoSourceAction extends BreakpointPanelAction {
  private final Project myProject;

  protected GotoSourceAction(final Project project) {
    super("Go to");
    myProject = project;
  }

  public void actionPerformed(ActionEvent e) {
    gotoSource();
  }

  private void gotoSource() {
    OpenFileDescriptor editSourceDescriptor = getPanel().createEditSourceDescriptor(myProject);
    if (editSourceDescriptor != null) {
      FileEditorManager.getInstance(myProject).openTextEditor(editSourceDescriptor, true);
    }
  }
  public void setButton(AbstractButton button) {
    super.setButton(button);
    getButton().setMnemonic('G');
  }

  public void setPanel(BreakpointPanel panel) {
    super.setPanel(panel);
    ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet();
    new AnAction() {
      public void actionPerformed(AnActionEvent e){
        gotoSource();
      }
    }.registerCustomShortcutSet(shortcutSet, getPanel().getPanel());
  }

  public void update() {
    getButton().setEnabled(getPanel().getCurrentViewableBreakpoint() != null);
  }
}
