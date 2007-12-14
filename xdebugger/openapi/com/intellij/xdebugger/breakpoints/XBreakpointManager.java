package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface XBreakpointManager {

  @NotNull <T extends XBreakpointProperties>
  XBreakpoint<T> addBreakpoint(XBreakpointType<T> type, @Nullable T properties);

  @NotNull <T extends XBreakpointProperties>
  XLineBreakpoint<T> addLineBreakpoint(XBreakpointType<T> type, @NotNull String fileUrl, int line, @Nullable T properties);
  
  void removeBreakpoint(@NotNull XBreakpoint<?> breakpoint);

  @NotNull 
  XBreakpoint[] getBreakpoints();

}
