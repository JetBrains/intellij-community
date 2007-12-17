package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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
  XBreakpoint<?>[] getAllBreakpoints();

  @NotNull <T extends XBreakpointProperties>
  Collection<? extends XBreakpoint<T>> getBreakpoints(@NotNull XBreakpointType<T> type);


  <T extends XBreakpointProperties>
  void addBreakpointListener(@NotNull XBreakpointType<T> type, @NotNull XBreakpointListener<T> listener);

  <T extends XBreakpointProperties>
  void removeBreakpointListener(@NotNull XBreakpointType<T> type, @NotNull XBreakpointListener<T> listener);

  <T extends XBreakpointProperties>
  void addBreakpointListener(@NotNull XBreakpointType<T> type, @NotNull XBreakpointListener<T> listener, Disposable parentDisposable);
}
