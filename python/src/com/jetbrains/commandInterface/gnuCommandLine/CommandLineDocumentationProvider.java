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

import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.commandInterface.command.Argument;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.command.Option;
import com.jetbrains.commandInterface.gnuCommandLine.psi.*;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides quick help for arguments
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineDocumentationProvider extends DocumentationProviderEx {
  @Nullable
  @Override
  public String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
    if (!(element instanceof CommandLinePart)) {
      return null;
    }

    final CommandLinePart commandLinePart = (CommandLinePart)element;
    final Command realCommand = commandLinePart.findRealCommand();
    if (realCommand == null) {
      return null;
    }

    if (element instanceof CommandLineFile) {
      return realCommand.getHelp(false); // We do not need arguments info in help text
    }

    final CommandLineElement commandLineElement = PyUtil.as(element, CommandLineElement.class);
    if (commandLineElement == null) {
      return null;
    }

    final Ref<String> resultText = new Ref<String>();

    commandLineElement.accept(new CommandLineVisitor() {
      @Override
      public void visitArgument(@NotNull CommandLineArgument o) {
        super.visitArgument(o);
        final CommandLineFile commandLineFile = commandLinePart.getCommandLineFile();
        if (commandLineFile == null) {
          return;
        }
        // Get argument by argument
        final ValidationResult validationResult = commandLineFile.getValidationResult();
        if (validationResult == null) {
          return;
        }

        final Option option = validationResult.getOptionForOptionArgument(o);
        if (option != null) {
          // Option argument should be documented by option
          resultText.set(option.getHelp());
          return;
        }

        // Probably positional
        final Argument argument = validationResult.getArgument(o);
        if (argument != null) {
          resultText.set(argument.getHelpText());
        }
      }

      @Override
      public void visitCommand(@NotNull CommandLineCommand o) {
        super.visitCommand(o);
        resultText.set(realCommand.getHelp(false));
      }

      @Override
      public void visitOption(@NotNull CommandLineOption o) {
        super.visitOption(o);
        for (Option option : realCommand.getOptions()) {
          if (option.getAllNames().contains(o.getOptionName())) {
            resultText.set(option.getHelp());
            return;
          }
        }
      }
    });
    final String help = resultText.get();
    // For some reason we can't return empty sting (leads to "fetchig doc" string)
    return (StringUtil.isEmptyOrSpaces(help) ? null : help);
  }


  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                  @NotNull PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    final CommandLineElement commandLineElement = PsiTreeUtil.getParentOfType(contextElement, CommandLineElement.class);
    if (commandLineElement != null) {
      return commandLineElement;
    }
    return PyUtil.as(file, CommandLineFile.class);
  }
}
