package com.intellij.xdebugger.breakpoints;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface XLineBreakpoint<T extends XBreakpointProperties> extends XBreakpoint<T> {

  int getLine();

  String getFileUrl();

  @NotNull
  XSourcePosition getSourcePosition();
}
