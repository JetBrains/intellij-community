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
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.ui.DebuggerIcons;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;

/**
 * @author nik
 */
public abstract class XBreakpointType<B extends XBreakpoint<P>, P extends XBreakpointProperties> {
  public static final ExtensionPointName<XBreakpointType> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.xdebugger.breakpointType");
  private @NonNls @NotNull String myId;
  private @Nls @NotNull String myTitle;
  private boolean mySuspendThreadSupported;

  protected XBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title) {
    this(id, title, false);
  }

  protected XBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title, boolean suspendThreadSupported) {
    myId = id;
    myTitle = title;
    mySuspendThreadSupported = suspendThreadSupported;
  }

  @Nullable
  public P createProperties() {
    return null;
  }

  public final boolean isSuspendThreadSupported() {
    return mySuspendThreadSupported;
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

  @Nullable 
  public XBreakpointCustomPropertiesPanel<B> createCustomConditionsPanel() {
    return null;
  }

  @Nullable
  public XBreakpointCustomPropertiesPanel<B> createCustomPropertiesPanel() {
    return null;
  }

  @Nullable
  public XDebuggerEditorsProvider getEditorsProvider() {
    return null;
  }

  public List<XBreakpointGroupingRule<B, ?>> getGroupingRules() {
    return Collections.emptyList();
  }

  @NotNull 
  public Comparator<B> getBreakpointComparator() {
    return XDebuggerUtil.getInstance().getDefaultBreakpointComparator(this);
  }

}
