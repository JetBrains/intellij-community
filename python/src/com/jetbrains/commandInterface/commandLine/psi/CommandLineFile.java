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
package com.jetbrains.commandInterface.commandLine.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.commandLine.CommandLineLanguage;
import com.jetbrains.commandInterface.commandLine.CommandLinePart;
import com.jetbrains.commandInterface.commandLine.ValidationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;


/**
 * Gnu command line file (topmost element).
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineFile extends PsiFileBase implements CommandLinePart {
  /**
   * List of commands, available on this file.
   */
  private static final Key<List<Command>> COMMANDS = Key.create("COMMANDS");

  public CommandLineFile(final FileViewProvider provider) {
    super(provider, CommandLineLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return getViewProvider().getFileType();
  }

  @Nullable
  @Override
  public CommandLineFile getCommandLineFile() {
    return this;
  }


  /**
   * @param commands list of commands, available for this file. Better to provide one, if you know
   */
  public void setCommands(@Nullable final List<Command> commands) {
    putCopyableUserData(COMMANDS, commands);
  }

  /**
   * @return List of commands, available on this file. May be null if no info available
   */
  @Nullable
  public List<Command> getCommands() {
    return getCopyableUserData(COMMANDS);
  }


  /**
   * Tries to find real command used in this file.
   * You need to first inject list of {@link #setCommands(List)}.
   *
   * @return Command if found and available, or null if command can't be parsed or bad command.
   */
  @Override
  @Nullable
  public Command findRealCommand() {
    final String command = getCommand();
    final List<Command> commands = getCommands();

    if (commands == null || command == null) {
      return null;
    }
    for (final Command realCommand : commands) {
      if (realCommand.getName().equals(command)) {
        return realCommand;
      }
    }

    return null;
  }

  /**
   * Tries to validate file.
   *
   * @return file validation info or null if file is junk or list of commands is unknown (see {@link #setCommands(List)})
   */
  @Nullable
  public ValidationResult getValidationResult() {
    return ValidationResultImpl.create(this);
  }

  /**
   * @return command (text) typed by user. I.e "my_command" in "my_command --foo --bar"
   */
  @Nullable
  public String getCommand() {
    final CommandLineCommand command = PsiTreeUtil.getChildOfType(this, CommandLineCommand.class);
    if (command != null) {
      return command.getText();
    }
    return null;
  }

  /**
   * @return all arguments from file
   */
  @NotNull
  public Collection<CommandLineArgument> getArguments() {
    return PsiTreeUtil.findChildrenOfType(this, CommandLineArgument.class);
  }

  /**
   * @return all options from file
   */
  @NotNull
  public Collection<CommandLineOption> getOptions() {
    return PsiTreeUtil.findChildrenOfType(this, CommandLineOption.class);
  }
}
