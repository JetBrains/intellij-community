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

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public interface XDebugSession {

  @NotNull
  Project getProject();

  @NotNull XDebugProcess getDebugProcess();

  boolean isPaused();
  boolean isSuspended();

  @Nullable
  XStackFrame getCurrentStackFrame();
  
  XSuspendContext getSuspendContext();

  @Nullable
  XSourcePosition getCurrentPosition();

  void stepOver(boolean ignoreBreakpoints);
  void stepInto();
  void stepOut();
  void forceStepInto();
  void runToPosition(@NotNull XSourcePosition position, final boolean ignoreBreakpoints);

  void resume();

  void showExecutionPoint();

  /**
   * Call this method to setup custom icon and/or error message (it will be shown in tooltip) for breakpoint
   * @param breakpoint breakpoint
   * @param icon icon (<code>null</code> if default icon should be used). You can use icons from {@link com.intellij.xdebugger.ui.DebuggerIcons}
   * @param errorMessage an error message if breakpoint isn't successfully registered
   */
  void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage);

  /**
   * Call this method when a breakpoint is reached. If the method returns <code>true</code> the underlying debugging process should be
   * suspended.
   * @param breakpoint reached breakpoint
   * @param suspendContext context
   * @return <code>true</code> if the debug process should be suspended
   */
  boolean breakpointReached(@NotNull XBreakpoint<?> breakpoint, @NotNull XSuspendContext suspendContext);

  /**
   * Call this method when position is reached (e.g. after "Run to cursor" or "Step over" command)
   * @param position current position
   * @param suspendContext context
   */
  void positionReached(@NotNull XSourcePosition position, @NotNull XSuspendContext suspendContext);


  void stop();

  boolean isStopped();

  void setBreakpointMuted(boolean muted);
  boolean areBreakpointsMuted();


  void addSessionListener(@NotNull XDebugSessionListener listener);
  void removeSessionListener(@NotNull XDebugSessionListener listener);


  @NotNull
  String getSessionName();

  @NotNull
  RunContentDescriptor getRunContentDescriptor();

}
