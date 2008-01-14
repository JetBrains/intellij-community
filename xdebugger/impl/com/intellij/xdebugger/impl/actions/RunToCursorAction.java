package com.intellij.xdebugger.impl.actions;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.impl.DebuggerSupport;

/**
 * @author nik
 */
public class RunToCursorAction extends XDebuggerActionBase {
  public RunToCursorAction() {
    super(true);
  }

  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getRunToCursorHandler();
  }
}
