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

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface XDebugSession {

  @NotNull
  Project getProject();

  @NotNull XDebugProcess getDebugProcess();

  boolean isPaused();

  void stepOver(boolean ignoreBreakpoints);
  void stepInto();
  void stepOut();
  void forceStepInto();
  void runToPosition(@NotNull XSourcePosition position, final boolean ignoreBreakpoints);

  void resume();

  void showExecutionPoint();


  void breakpointReached(@NotNull XBreakpoint<?> breakpoint);
  void positionReached(@NotNull XSourcePosition position);


  void stop();

  void setBreakpointMuted(boolean muted);
  boolean areBreakpointsMuted();
}
