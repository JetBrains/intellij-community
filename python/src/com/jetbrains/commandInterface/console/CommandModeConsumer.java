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

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.Consumer;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.command.CommandExecutor;
import com.jetbrains.commandInterface.commandLine.CommandLineLanguage;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Supports {@link CommandConsole} in "command-mode"
 * Delegates console execution to command.
 *
 * @author Ilya.Kazakevich
 */
final class CommandModeConsumer implements Consumer<String> {
  @NotNull
  private static final Pattern EMPTY_SPACE = Pattern.compile("\\s+");
  @NotNull
  private final Collection<Command> myCommands;
  @NotNull
  private final Module myModule;
  @NotNull
  private final LanguageConsoleImpl myConsole;
  /**
   * To be used when user runs unknown command
   */
  @Nullable
  private final CommandExecutor myDefaultExecutor;

  /**
   * @param commands        known commands (may be null, default executor should always be used then)
   * @param module          module
   * @param console         console where to execute them (if any)
   * @param defaultExecutor default executor to execute unknown commands.
   *                        User will get "unknown command" if command is unknown and
   *                        no executor provided.
   */
  CommandModeConsumer(@Nullable final Collection<Command> commands,
                      @NotNull final Module module,
                      @NotNull final LanguageConsoleImpl console,
                      @Nullable final CommandExecutor defaultExecutor) {
    myCommands = commands != null ? new ArrayList<>(commands) : Collections.<Command>emptyList();
    myModule = module;
    myConsole = console;
    myDefaultExecutor = defaultExecutor;
  }

  @Override
  public void consume(final String t) {
    /**
     * We need to: 1) parse input 2) fetch command 3) split its arguments.
     */
    final PsiFileFactory fileFactory = PsiFileFactory.getInstance(myModule.getProject());
    final CommandLineFile file = PyUtil.as(fileFactory.createFileFromText(CommandLineLanguage.INSTANCE, t), CommandLineFile.class);
    if (file == null) {
      return;
    }
    final String commandName = file.getCommand();
    final List<String> commandAndArgs = Arrays.asList(EMPTY_SPACE.split(file.getText().trim()));
    // 1 because we need to remove command which is on the first place
    final List<String> args =
      (commandAndArgs.size() > 1 ? commandAndArgs.subList(1, commandAndArgs.size()) : Collections.<String>emptyList());
    for (final Command command : myCommands) {
      if (command.getName().equals(commandName)) {

        command.execute(commandName, myModule, args, myConsole);
        return;
      }
    }
    if (myDefaultExecutor != null && !commandAndArgs.isEmpty()) {
      // Unknown command execution is delegated to default executor
      myDefaultExecutor.execute(commandAndArgs.get(0), myModule, args, myConsole);
    }
    else {
      myConsole.print(PyBundle.message("commandLine.commandNotFound", commandName), ConsoleViewContentType.ERROR_OUTPUT);
      myConsole.print("", ConsoleViewContentType.SYSTEM_OUTPUT);
    }
  }
}
