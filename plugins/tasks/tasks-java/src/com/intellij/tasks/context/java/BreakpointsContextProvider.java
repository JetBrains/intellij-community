// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context.java;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.tasks.context.WorkingContextProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class BreakpointsContextProvider extends WorkingContextProvider {
  private final DebuggerManager myDebuggerManager;

  public BreakpointsContextProvider(DebuggerManager debuggerManager) {
    myDebuggerManager = debuggerManager;
  }

  @NotNull
  @Override
  public String getId() {
    return "javaDebugger";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Java Debugger breakpoints";
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element toElement) throws WriteExternalException {
    ((DebuggerManagerEx)myDebuggerManager).getBreakpointManager().writeExternal(toElement);
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) throws InvalidDataException {
    //noinspection unchecked
    ((PersistentStateComponent<Element>)myDebuggerManager).loadState(fromElement);
  }

  @Override
  public void clearContext(@NotNull Project project) {
    final BreakpointManager breakpointManager = ((DebuggerManagerEx)myDebuggerManager).getBreakpointManager();
    List<Breakpoint> breakpoints = breakpointManager.getBreakpoints();
    for (final Breakpoint breakpoint : breakpoints) {
      ApplicationManager.getApplication().runWriteAction(() -> breakpointManager.removeBreakpoint(breakpoint));
    }
  }
}
