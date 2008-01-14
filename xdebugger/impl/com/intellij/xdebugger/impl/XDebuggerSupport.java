package com.intellij.xdebugger.impl;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelProvider;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerSteppingActionHandler;
import com.intellij.xdebugger.impl.actions.handlers.XToggleLineBreakpointActionHandler;

/**
 * @author nik
 */
public class XDebuggerSupport extends DebuggerSupport {
  private XBreakpointPanelProvider myBreakpointPanelProvider;
  private XToggleLineBreakpointActionHandler myToggleLineBreakpointActionHandler;

  public XDebuggerSupport() {
    myBreakpointPanelProvider = new XBreakpointPanelProvider();
    myToggleLineBreakpointActionHandler = new XToggleLineBreakpointActionHandler();
  }

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

  @NotNull
  public DebuggerActionHandler getRunToCursorHandler() {
    return new XDebuggerSteppingActionHandler();
  }

  @NotNull
  public DebuggerActionHandler getForceRunToCursorHandler() {
    return new XDebuggerSteppingActionHandler();
  }

  @NotNull
  public DebuggerActionHandler getResumeActionHandler() {
    return new XDebuggerSteppingActionHandler();
  }

  @NotNull
  public DebuggerActionHandler getPauseHandler() {
    return new XDebuggerSteppingActionHandler();
  }

  @NotNull
  public DebuggerActionHandler getToggleLineBreakpointHandler() {
    return myToggleLineBreakpointActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getShowExecutionPointHandler() {
    return new XDebuggerSteppingActionHandler();
  }
}
