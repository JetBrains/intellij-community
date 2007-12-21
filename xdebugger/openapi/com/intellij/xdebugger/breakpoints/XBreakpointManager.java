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
  XBreakpoint<T> addBreakpoint(XBreakpointType<XBreakpoint<T>, T> type, @Nullable T properties);

  @NotNull <T extends XBreakpointProperties>
  XLineBreakpoint<T> addLineBreakpoint(XBreakpointType<XLineBreakpoint<T>, T> type, @NotNull String fileUrl, int line, @Nullable T properties);
  
  void removeBreakpoint(@NotNull XBreakpoint<?> breakpoint);


  @NotNull 
  XBreakpoint<?>[] getAllBreakpoints();

  @NotNull <P extends XBreakpointProperties, B extends XBreakpoint<P>>
  Collection<? extends B> getBreakpoints(@NotNull XBreakpointType<B, P> type);


  <B extends XBreakpoint<P>, P extends XBreakpointProperties>
  void addBreakpointListener(@NotNull XBreakpointType<B, P> type, @NotNull XBreakpointListener<B> listener);

  <B extends XBreakpoint<P>, P extends XBreakpointProperties>
  void removeBreakpointListener(@NotNull XBreakpointType<B, P> type, @NotNull XBreakpointListener<B> listener);

  <B extends XBreakpoint<P>, P extends XBreakpointProperties>
  void addBreakpointListener(@NotNull XBreakpointType<B, P> type, @NotNull XBreakpointListener<B> listener, Disposable parentDisposable);
}
