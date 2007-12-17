package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public interface XBreakpoint<T extends XBreakpointProperties> {

  boolean isEnabled();
  void setEnabled(boolean enabled);

  boolean isValid();

  @NotNull
  Icon getIcon();

  @NotNull
  XBreakpointType getType();

  T getProperties();
}
