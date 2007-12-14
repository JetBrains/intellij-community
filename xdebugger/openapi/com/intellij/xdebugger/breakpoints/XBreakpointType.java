package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XBreakpointType<T extends XBreakpointProperties> {
  public static final XBreakpointType<XBreakpointProperties> LINE_BREAKPOINT_TYPE = new XBreakpointType<XBreakpointProperties>("line", "Line") {
  };
  private @NonNls @NotNull String myId;
  private @Nls @NotNull String myTitle;

  protected XBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title) {
    myId = id;
    myTitle = title;
  }

  @Nullable
  public static XBreakpointType<?> findType(@NotNull @NonNls String id) {
    if ("line".equals(id)) {
      return LINE_BREAKPOINT_TYPE;
    }
    return null;
  }

  @Nullable 
  public T createProperties() {
    return null;
  }

  @NotNull
  public final String getId() {
    return myId;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }
}
