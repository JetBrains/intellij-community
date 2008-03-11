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

package com.intellij.xdebugger;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XDebugProcess {
  private final XDebugSession mySession;

  protected XDebugProcess(@NotNull XDebugSession session) {
    mySession = session;
  }

  public final XDebugSession getSession() {
    return mySession;
  }

  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return XBreakpointHandler.EMPTY_ARRAY;
  }

  @Nullable
  public XDebuggerEditorsProvider getEditorsProvider() {
    return null;
  }

  public void sessionInitialized() {
  }

  public abstract void startStepOver();

  public abstract void startStepInto();

  public abstract void startStepOut();

  public abstract void stop();

  public abstract void resume();

  public abstract void runToPosition(@NotNull XSourcePosition position);

  @Nullable
  public ProcessHandler getProcessHandler() {
    return null;
  }

  @NotNull
  public ExecutionConsole createConsole() {
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(getSession().getProject());
    return consoleBuilder.getConsole();
  }

  public String getCurrentStateMessage() {
    return mySession.isStopped() ? XDebuggerBundle.message("debugger.state.message.disconnected")
           : XDebuggerBundle.message("debugger.state.message.connected");
  }
}
