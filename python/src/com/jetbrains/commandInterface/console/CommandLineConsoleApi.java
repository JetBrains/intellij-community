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
package com.jetbrains.commandInterface.console;

import com.intellij.execution.console.LanguageConsoleBuilder;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.command.CommandExecutor;
import com.jetbrains.toolWindowWithActions.WindowWithActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Displays command-line console for user.
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineConsoleApi {

  private CommandLineConsoleApi() {
  }

  /**
   * Creates and displays command-line console for user.
   *
   * @param module                     module to display console for.
   * @param consoleName                Console name (would be used in prompt, history etc)
   * @param commandsAndDefaultExecutor list of commands available for this console. You may pass null here, but in this case no validation nor suggestion will be available.
   *                                   Default executor (may be null) to be used when user executes unknwon command
   *                                   Whole pair be null, but no executor would be registered, so you will need to use
   *                                   {@link LanguageConsoleBuilder#registerExecuteAction(LanguageConsoleView, Consumer, String, String, Condition)}
   *                                   by yourself passing this method result as arg to enable execution, history etc.
   * @return newly created console. You do not need to do anything with this value to display console: it will be displayed automatically
   */
  @NotNull
  public static LanguageConsoleView createConsole(
    @NotNull final Module module,
    @NotNull final String consoleName,
    @Nullable final Pair<List<Command>, CommandExecutor> commandsAndDefaultExecutor) {
    return createConsole(module, consoleName, consoleName, commandsAndDefaultExecutor);
  }


  @NotNull
  public static LanguageConsoleView createConsole(
    @NotNull final Module module,
    @NotNull final String consoleName,
    @NotNull final String promptName,
    @Nullable final Pair<List<Command>, CommandExecutor> commandsAndDefaultExecutor) {
    final Project project = module.getProject();
    final CommandConsole console = CommandConsole.createConsole(module, promptName, commandsAndDefaultExecutor);

    // Show console on "toolwindow"
    WindowWithActions.showConsoleWithProcess(console,
                                             console.getEditor().getComponent(),
                                             consoleName,
                                             project,
                                             null);

    ArgumentHintLayer.attach(console); // Display [arguments]
    return console;
  }
}

