/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.toolWindowWithActions;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import org.jetbrains.annotations.Nullable;

// TODO: Move to the same package as ConsoleView
/**
 * Console that knows how to run process. Such console stores somewhere {@link ProcessHandler} passed
 * to {@link #attachToProcess(ProcessHandler)} and may return it via {@link #getProcessHandler()}
 *
 * @author Ilya.Kazakevich
 */
public interface ConsoleWithProcess extends ConsoleView {
  /**
   * @return process handler of process currently running or null if no such process
   */
  @Nullable
  ProcessHandler getProcessHandler();
}
