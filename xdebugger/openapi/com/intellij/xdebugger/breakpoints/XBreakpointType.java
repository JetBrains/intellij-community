package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;

/**
 * @author nik
 */
public abstract class XBreakpointType<B extends XBreakpoint<P>, P extends XBreakpointProperties> {
  public static final ExtensionPointName<XBreakpointType> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.xdebugger.breakpointType");
  private @NonNls @NotNull String myId;
  private @Nls @NotNull String myTitle;

  protected XBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title) {
    myId = id;
    myTitle = title;
  }

  @Nullable
  public static XBreakpointType<?,?> findType(@NotNull @NonNls String id) {
    XBreakpointType[] breakpointTypes = Extensions.getExtensions(EXTENSION_POINT_NAME);
    for (XBreakpointType breakpointType : breakpointTypes) {
      if (id.equals(breakpointType.getId())) {
        return breakpointType;
      }
    }
    return null;
  }

  @Nullable 
  public P createProperties() {
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
