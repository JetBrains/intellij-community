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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.command.CommandExecutor;
import com.jetbrains.commandInterface.command.Help;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineCommand;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ref to be injected in command itself
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineCommandReference extends CommandLineElementReference<CommandLineCommand> {

  CommandLineCommandReference(@NotNull final CommandLineCommand element) {
    super(element);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return null;
  }


  @NotNull
  @Override
  public Object[] getVariants() {
    final CommandLineFile file = getCommandLineFile();
    if (file == null) {
      return EMPTY_ARRAY;
    }
    final List<Command> commands = file.getCommands();
    if (commands == null) {
      return EMPTY_ARRAY;
    }

    final LookupWithIndentsBuilder result = new LookupWithIndentsBuilder();

    for (final Command command : commands) {
      final LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(command.getName());
      final Help help = command.getHelp(true);
      result.addElement(lookupElementBuilder, (help != null ? help.getHelpString() : null));
    }


    return result.getResult();
  }
}
