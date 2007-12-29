package com.intellij.xdebugger.impl;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelProvider;

/**
 * @author nik
 */
public class XDebuggerSupport extends DebuggerSupport {
  private XBreakpointPanelProvider myBreakpointPanelProvider = new XBreakpointPanelProvider();

  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }
}
