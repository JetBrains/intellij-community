package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XLineBreakpointType<P extends XBreakpointProperties> extends XBreakpointType<XLineBreakpoint<P>,P> {
  protected XLineBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title) {
    super(id, title);
  }

  public abstract boolean canPutAt(@NotNull VirtualFile file, int line);

  public abstract P createBreakpointProperties(@NotNull VirtualFile file, int line);

  public abstract String getDisplayText(final XLineBreakpoint<P> breakpoint);
}
