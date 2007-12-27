package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class XBreakpointPanelAction<B extends XBreakpoint<?>> {
  protected final XBreakpointsPanel<B> myBreakpointsPanel;
  private final String myName;

  protected XBreakpointPanelAction(final @NotNull XBreakpointsPanel<B> breakpointsPanel, @NotNull String name) {
    myBreakpointsPanel = breakpointsPanel;
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public abstract boolean isEnabled(@NotNull Collection<? extends B> breakpoints);

  public abstract void perform(@NotNull Collection<? extends B> breakpoints);

}
