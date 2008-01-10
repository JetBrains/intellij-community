package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.XDebuggerSupport;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerActionBase extends AnAction {

  public void update(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      DebuggerSupport[] debuggerSupports = XDebuggerSupport.getDebuggerSupports();
      for (DebuggerSupport support : debuggerSupports) {
        if (isEnabled(project, e, support)) {
          e.getPresentation().setEnabled(true);
          return;
        }
      }
    }
    e.getPresentation().setEnabled(false);
  }

  @NotNull
  protected abstract DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport);

  private boolean isEnabled(final Project project, final AnActionEvent event, final DebuggerSupport support) {
    return getHandler(support).isEnabled(project, event);
  }

  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    DebuggerSupport[] debuggerSupports = XDebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : debuggerSupports) {
      if (isEnabled(project, e, support)) {
        perform(project, e, support);
      }
    }
  }

  private void perform(final Project project, final AnActionEvent e, final DebuggerSupport support) {
    getHandler(support).perform(project, e);
  }
}
