package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class XBreakpointCustomPropertiesPanel<B extends XBreakpoint<?>> {

  @NotNull
  public abstract JComponent getComponent();

  public abstract void saveTo(@NotNull B breakpoint);

  public abstract void loadFrom(@NotNull B breakpoint);

  public void dispose() {
  }
}
