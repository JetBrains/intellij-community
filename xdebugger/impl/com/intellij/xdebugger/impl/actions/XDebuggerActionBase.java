package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.XDebuggerSupport;
import com.intellij.xdebugger.impl.DebuggerSupport;

/**
 * @author nik
 */
public abstract class XDebuggerActionBase extends AnAction {

  public void update(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      DebuggerSupport[] debuggerSupports = XDebuggerSupport.getDebuggerSupports();
      for (DebuggerSupport support : debuggerSupports) {
        if (isEnabled(project, support)) {
          e.getPresentation().setEnabled(true);
          return;
        }
      }
    }
    e.getPresentation().setEnabled(false);
  }

  protected boolean isEnabled(final Project project, final DebuggerSupport support) {
    return true;
  }

  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    DebuggerSupport[] debuggerSupports = XDebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : debuggerSupports) {
      if (isEnabled(project, support)) {
        perform(project, support);
      }
    }
  }

  protected abstract void perform(final Project project, final DebuggerSupport support);
}
