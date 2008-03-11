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

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XDebuggerManager {

  public static XDebuggerManager getInstance(@NotNull Project project) {
    return project.getComponent(XDebuggerManager.class);
  }

  @NotNull
  public abstract XBreakpointManager getBreakpointManager();


  @NotNull
  public abstract XDebugSession[] getDebugSessions();

  @Nullable
  public abstract XDebugSession getCurrentSession();

  @NotNull
  public abstract XDebugSession startSession(@NotNull final ProgramRunner runner,
                                             @NotNull ExecutionEnvironment env,
                                             @Nullable RunContentDescriptor contentToReuse,
                                             @NotNull XDebugProcessStarter processStarter);

}
