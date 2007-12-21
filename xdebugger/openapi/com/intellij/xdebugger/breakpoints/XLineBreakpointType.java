package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author nik
 */
public abstract class XLineBreakpointType<P extends XBreakpointProperties> extends XBreakpointType<XLineBreakpoint<P>,P> {
  protected XLineBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title) {
    super(id, title);
  }

  @Nullable
  public abstract P createBreakpointProperties(@NotNull VirtualFile file, int line);  
}
