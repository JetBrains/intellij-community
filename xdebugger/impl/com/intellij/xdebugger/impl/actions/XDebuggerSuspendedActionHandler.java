package com.intellij.xdebugger.impl.actions;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerSuspendedActionHandler extends XDebuggerActionHandler {

  protected boolean isEnabled(final @NotNull XDebugSession session, final DataContext dataContext) {
    return session.isPaused();
  }
}
