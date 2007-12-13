package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XBreakpointType<T extends XBreakpointProperties> {
  public static final XBreakpointType<XLineBreakpointProperties> LINE_BREAKPOINT_TYPE = new XBreakpointType<XLineBreakpointProperties>("line", "Line", XLineBreakpointProperties.class) {
  };
  private @NonNls @NotNull String myId;
  private @Nls @NotNull String myTitle;
  private final Class<T> myPropertiesClass;

  protected XBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title, @NotNull Class<T> propertiesClass) {
    myId = id;
    myTitle = title;
    myPropertiesClass = propertiesClass;
  }

  @Nullable
  public static XBreakpointType findType(@NotNull @NonNls String id) {
    if ("line".equals(id)) {
      return LINE_BREAKPOINT_TYPE;
    }
    return null;
  }

  public final Class<T> getPropertiesClass() {
    return myPropertiesClass;
  }

  @NotNull
  public final String getId() {
    return myId;
  }
}
