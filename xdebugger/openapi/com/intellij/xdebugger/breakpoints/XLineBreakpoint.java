package com.intellij.xdebugger.breakpoints;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface XLineBreakpoint<P extends XBreakpointProperties> extends XBreakpoint<P> {

  int getLine();

  String getFileUrl();

  @NotNull
  XSourcePosition getSourcePosition();

  @NotNull
  XLineBreakpointType<P> getType();
}
