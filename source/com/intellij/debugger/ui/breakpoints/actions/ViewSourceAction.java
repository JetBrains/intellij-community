/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public class ViewSourceAction extends BreakpointPanelAction {
  private final Project myProject;

  public ViewSourceAction(final Project project) {
    super(IdeBundle.message("button.view.source"));
    myProject = project;
  }

  public void setButton(AbstractButton button) {
    super.setButton(button);
  }

  public void actionPerformed(ActionEvent e) {
    OpenFileDescriptor editSourceDescriptor = getPanel().createEditSourceDescriptor(myProject);
    if (editSourceDescriptor != null) {
      FileEditorManager.getInstance(myProject).openTextEditor(editSourceDescriptor, false);
    }
  }

  public void update() {
    getButton().setEnabled(getPanel().getCurrentViewableBreakpoint() != null);
  }
}
