package com.intellij.debugger.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.DebuggerManagerEx;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ForceStepOverAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession().stepOver(true);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isPaused());
  }
}

