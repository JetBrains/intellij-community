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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.ui.DebuggerIcons;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;

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

  @NotNull  
  public Icon getDisabledDependentIcon() {
    return DebuggerIcons.DISABLED_DEPENDENT_BREAKPOINT_ICON;
  }

  @NotNull
  public Comparator<XLineBreakpoint<P>> getBreakpointComparator() {
    return XDebuggerUtil.getInstance().getDefaultLineBreakpointComparator();
  }

}
