package com.intellij.xdebugger.impl;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelProvider;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerSteppingActionHandler;

/**
 * @author nik
 */
public class XDebuggerSupport extends DebuggerSupport {
  private XBreakpointPanelProvider myBreakpointPanelProvider = new XBreakpointPanelProvider();

  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  @NotNull
  public DebuggerActionHandler getStepOverHandler() {
    return new XDebuggerSteppingActionHandler();
  }

  @NotNull
  public DebuggerActionHandler getStepIntoHandler() {
    return new XDebuggerSteppingActionHandler();
  }

  @NotNull
  public DebuggerActionHandler getStepOutHandler() {
    return new XDebuggerSteppingActionHandler();
  }

  @NotNull
  public DebuggerActionHandler getForceStepOverHandler() {
    return new XDebuggerSteppingActionHandler();
  }

  @NotNull
  public DebuggerActionHandler getForceStepIntoHandler() {
    return new XDebuggerSteppingActionHandler();
  }
}
