package com.intellij.xdebugger.breakpoints;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public interface XBreakpoint<P extends XBreakpointProperties> {

  boolean isEnabled();
  void setEnabled(boolean enabled);

  boolean isValid();

  @NotNull
  Icon getIcon();

  @NotNull
  XBreakpointType<?,P> getType();

  P getProperties();

  @Nullable
  XSourcePosition getSourcePosition();
}
