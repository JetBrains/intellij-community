package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
*/
public class XDebuggerRunToCursorActionHandler extends XDebuggerSuspendedActionHandler {
  private final boolean myIgnoreBreakpoints;

  public XDebuggerRunToCursorActionHandler(final boolean ignoreBreakpoints) {
    myIgnoreBreakpoints = ignoreBreakpoints;
  }

  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
  }
}
