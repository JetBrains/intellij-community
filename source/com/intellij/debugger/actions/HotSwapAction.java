package com.intellij.debugger.actions;

import com.intellij.execution.RunManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.ui.HotSwapUI;
import com.intellij.debugger.impl.DebuggerSession;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 26, 2003
 * Time: 12:52:01 PM
 * To change this template use Options | File Templates.
 */
public class HotSwapAction extends AnAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.actions.HotSwapAction");

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if(project == null) {
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

    if(session != null && session.isAttached()) {
      HotSwapUI.getInstance(project).reloadChangedClasses(session, RunManager.getInstance(project).getConfig().isCompileBeforeRunning());
    }
  }

  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if(project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

    e.getPresentation().setEnabled(session != null && session.isAttached() && session.getProcess().canRedefineClasses());
  }
}
