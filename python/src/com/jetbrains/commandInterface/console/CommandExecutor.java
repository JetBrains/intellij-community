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

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.Consumer;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.commandLine.CommandLineLanguage;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Delegates console execution to command.
 *
 * @author Ilya.Kazakevich
 */
class CommandExecutor implements Consumer<String> {
  @NotNull
  private static final Pattern EMPTY_SPACE = Pattern.compile("\\s+");
  @NotNull
  private final Collection<Command> myCommands = new ArrayList<Command>();
  @NotNull
  private final Module myModule;

  CommandExecutor(@NotNull final Collection<Command> commands, @NotNull final Module module) {
    myCommands.addAll(commands);
    myModule = module;
  }
  @Override
  public final void consume(final String t) {
    /**
     * We need to: 1) parse input 2) fetch command 3) split its arguments.
     */
    final PsiFileFactory fileFactory = PsiFileFactory.getInstance(myModule.getProject());
    final CommandLineFile file = PyUtil.as(fileFactory.createFileFromText(CommandLineLanguage.INSTANCE, t), CommandLineFile.class);
    if (file == null) {
      return;
    }
    final String commandName = file.getCommand();

    for (final Command command : myCommands) {
      if (command.getName().equals(commandName)) {
        final List<String> argument = Arrays.asList(EMPTY_SPACE.split(file.getText().trim()));
        // 1 because we need to command which is on the first place
        command.execute(myModule, argument.subList(1, argument.size()));
      }
    }
  }
}
