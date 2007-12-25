/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
    XBreakpointType[] breakpointTypes = getBreakpointTypes();
    for (XBreakpointType breakpointType : breakpointTypes) {
      if (id.equals(breakpointType.getId())) {
        return breakpointType;
      }
    }
    return null;
  }

  public static XBreakpointType[] getBreakpointTypes() {
    return Extensions.getExtensions(EXTENSION_POINT_NAME);
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

  @NotNull 
  public Icon getEnabledIcon() {
    return DebuggerIcons.ENABLED_BREAKPOINT_ICON;
  }

  @NotNull
  public Icon getDisabledIcon() {
    return DebuggerIcons.DISABLED_BREAKPOINT_ICON;
  }

  public abstract String getDisplayText(B breakpoint);
}
