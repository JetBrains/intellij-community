package com.intellij.xdebugger.impl.breakpoints.ui.actions;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelAction;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointsPanel;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.Result;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
*/
public class RemoveBreakpointAction<B extends XBreakpoint<?>> extends XBreakpointPanelAction<B> {
  public RemoveBreakpointAction(final XBreakpointsPanel<B> panel) {
    super(panel, XDebuggerBundle.message("xbreakpoints.dialog.button.remove"));
  }

  public boolean isEnabled(@NotNull final Collection<? extends B> breakpoints) {
    return !breakpoints.isEmpty();
  }

  public void perform(@NotNull final Collection<? extends B> breakpoints) {
    final XBreakpointManager breakpointManager = myBreakpointsPanel.getBreakpointManager();
    new WriteAction() {
      protected void run(final Result result) {
        for (B breakpoint : breakpoints) {
          breakpointManager.removeBreakpoint(breakpoint);
        }
      }
    }.execute();
    myBreakpointsPanel.resetBreakpoints();
  }
}
