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

import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.command.Help;
import com.jetbrains.commandInterface.command.Option;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineArgument;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineCommand;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineOption;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineVisitor;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Provides quick help for arguments
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineDocumentationProvider extends DocumentationProviderEx {
  @Nullable
  @Override
  public String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
    final Help help = findHelp(element);
    if (help == null) {
      return null;
    }

    final String helpText = help.getHelpString();
    // For some reason we can't return empty sting (leads to "fetching doc" string)
    return (StringUtil.isEmptyOrSpaces(helpText) ? null : helpText);
  }

  @Override
  public List<String> getUrlFor(final PsiElement element, final PsiElement originalElement) {
    final Help help = findHelp(element);
    if (help == null) {
      return null;
    }
    final String externalHelpUrl = help.getExternalHelpUrl();
    if (externalHelpUrl != null) {
      return Collections.singletonList(externalHelpUrl);
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull final Editor editor,
                                                  @NotNull final PsiFile file,
                                                  @Nullable final PsiElement contextElement) {

    // First we try to find required parent for context element. Then, for element to the left of caret to support case "command<caret>"
    for (final PsiElement element : Arrays.asList(contextElement, file.findElementAt(editor.getCaretModel().getOffset() - 1))) {
      final CommandLineElement commandLineElement = PsiTreeUtil.getParentOfType(element, CommandLineElement.class);
      if (commandLineElement != null) {
        return commandLineElement;
      }
    }
    return null;
  }

  /**
   * Searches for help text for certain element
   *
   * @param element element to search help for
   * @return help or
   */
  @Nullable
  private static Help findHelp(@NotNull final PsiElement element) {
    if (!(element instanceof CommandLinePart)) {
      return null;
    }

    final CommandLinePart commandLinePart = (CommandLinePart)element;
    final Command realCommand = commandLinePart.findRealCommand();
    if (realCommand == null) {
      return null;
    }

    final CommandLineElement commandLineElement = PyUtil.as(element, CommandLineElement.class);
    if (commandLineElement == null) {
      return null;
    }


    final MyCommandHelpObtainer helpObtainer = new MyCommandHelpObtainer();
    commandLineElement.accept(helpObtainer);

    return helpObtainer.myResultHelp;
  }

  /**
   * Fetches text from command line part as visitor
   */
  private static final class MyCommandHelpObtainer extends CommandLineVisitor {
    private Help myResultHelp;

    @Override
    public void visitArgument(@NotNull final CommandLineArgument o) {
      super.visitArgument(o);
      myResultHelp = o.findBestHelp();
    }

    @Override
    public void visitCommand(@NotNull CommandLineCommand o) {
      super.visitCommand(o);
      final Command realCommand = o.findRealCommand();
      if (realCommand != null) {
        myResultHelp = realCommand.getHelp(false); // Arguments are ok to display here;
      }
    }

    @Override
    public void visitOption(@NotNull final CommandLineOption o) {
      super.visitOption(o);
      final Option option = o.findRealOption();
      if (option == null) {
        return;
      }
      myResultHelp = option.getHelp();
    }
  }
}