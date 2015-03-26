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
package com.jetbrains.commandInterface.command;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Executes command
 * @author Ilya.Kazakevich
 */
public interface CommandExecutor {
  /**
   *
   * @param commandName command name
   * @param module module where execution takes place
   * @param parameters command arguments
   */
  void execute(@NotNull String commandName, @NotNull Module module, @NotNull List<String> parameters);
}
