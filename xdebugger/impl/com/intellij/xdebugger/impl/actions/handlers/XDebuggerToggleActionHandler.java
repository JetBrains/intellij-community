package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerToggleActionHandler extends DebuggerToggleActionHandler {
  public final boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return session != null && isEnabled(session, event);
  }

  public boolean isSelected(@NotNull final Project project, final AnActionEvent event) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return session != null && isSelected(session, event);
  }

  public void setSelected(@NotNull final Project project, final AnActionEvent event, final boolean state) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session != null) {
      setSelected(session, event, state);
    }
  }

  protected abstract boolean isEnabled(final XDebugSession session, final AnActionEvent event);

  protected abstract boolean isSelected(final XDebugSession session, final AnActionEvent event);

  protected abstract void setSelected(final XDebugSession session, final AnActionEvent event, boolean state);
}
