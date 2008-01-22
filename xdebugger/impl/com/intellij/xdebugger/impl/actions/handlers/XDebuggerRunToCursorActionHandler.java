package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
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

  protected boolean isEnabled(final @NotNull XDebugSession session, final DataContext dataContext) {
    return super.isEnabled(session, dataContext) && XDebuggerUtilImpl.getCaretPosition(session.getProject(), dataContext) != null;
  }

  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(session.getProject(), dataContext);
    if (position != null) {
      session.runToPosition(position, myIgnoreBreakpoints);
    }
  }
}
