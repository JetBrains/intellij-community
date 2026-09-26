// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context.java;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.context.WorkingContextProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class BreakpointsContextProvider extends WorkingContextProvider {
  @Override
  public @NotNull String getId() {
    return "javaDebugger";
  }

  @Override
  public @NotNull String getDescription() {
    return TaskBundle.message("java.debugger.breakpoints");
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element toElement) throws WriteExternalException {
    getBreakpointManager(project).writeExternal(toElement);
  }

  private static @NotNull BreakpointManager getBreakpointManager(@NotNull Project project) {
    return DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) throws InvalidDataException {
    ((DebuggerManagerImpl)DebuggerManager.getInstance(project)).loadState(fromElement);
  }

  @Override
  public void clearContext(@NotNull Project project) {
    final BreakpointManager breakpointManager = getBreakpointManager(project);
    for (final Breakpoint breakpoint : breakpointManager.getBreakpoints()) {
      ApplicationManager.getApplication().runWriteAction(() -> breakpointManager.removeBreakpoint(breakpoint));
    }
  }
}
