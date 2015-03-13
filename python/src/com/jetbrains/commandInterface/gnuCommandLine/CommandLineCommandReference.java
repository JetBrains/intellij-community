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
package com.jetbrains.commandInterface.gnuCommandLine;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.commandInterface.gnuCommandLine.psi.CommandLineCommand;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.gnuCommandLine.psi.CommandLineFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Ref to be injected in command itself
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

    final Collection<LookupElement> commandNames = new ArrayList<LookupElement>();

    for (final Command command : commands) {
      LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(command.getName());
      final String help = command.getHelp(true);
      if (!StringUtil.isEmpty(help)) {
        lookupElementBuilder = lookupElementBuilder.withTailText(" :" + help);
      }
      commandNames.add(lookupElementBuilder);
    }


    return ArrayUtil.toObjectArray(commandNames);
  }
}
