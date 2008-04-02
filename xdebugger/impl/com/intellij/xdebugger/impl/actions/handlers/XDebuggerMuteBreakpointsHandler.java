package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * @author nik
*/
public class XDebuggerMuteBreakpointsHandler extends XDebuggerToggleActionHandler {
  protected boolean isEnabled(final XDebugSession session, final AnActionEvent event) {
    return true;
  }

  protected boolean isSelected(final XDebugSession session, final AnActionEvent event) {
    return session.areBreakpointsMuted();
  }

  protected void setSelected(final XDebugSession session, final AnActionEvent event, final boolean state) {
    session.setBreakpointMuted(state);
  }
}
