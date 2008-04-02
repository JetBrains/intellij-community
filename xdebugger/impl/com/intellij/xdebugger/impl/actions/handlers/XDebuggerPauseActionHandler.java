package com.intellij.xdebugger.impl.actions.handlers;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.openapi.actionSystem.DataContext;

/**
 * @author nik
*/
public class XDebuggerPauseActionHandler extends XDebuggerActionHandler {
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    session.pause();
  }

  protected boolean isEnabled(@NotNull final XDebugSession session, final DataContext dataContext) {
    return !session.isPaused();
  }
}
