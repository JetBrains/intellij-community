// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.commandInterfaceConsole;

import com.intellij.codeInsight.template.impl.TemplateColors;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.commandInterface.command.Argument;
import com.intellij.commandInterface.commandLine.ValidationResult;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;

/**
 * Displays argument hint (if required).
 * Will register {@link LinearGradientPaint} for the console editor.
 *
 * @author Ilya.Kazakevich
 */
final class ArgumentHintLayer {

  /**
   * Braces for mandatory args are [] according to GNU/POSIX recommendations
   */
  @NotNull
  private static final Pair<String, String> MANDATORY_ARG_BRACES = Pair.create("<", ">");
  /**
   * Braces for optional args are &lt;&gt; according to GNU/POSIX recommendations
   */
  @NotNull
  private static final Pair<String, String> OPTIONAL_ARG_BRACES = Pair.create("[", "]");

  private ArgumentHintLayer() {}

  /**
   * Adds appropriate braces to argument help text if needed
   *
   * @param required   is argument required
   * @param textToShow arg help text
   * @return text to show
   */
  @NotNull
  private static String wrapBracesIfNeeded(final boolean required, @NotNull final String textToShow) {
    if (textToShow.startsWith(MANDATORY_ARG_BRACES.first) || textToShow.startsWith(OPTIONAL_ARG_BRACES.first)) {
      return textToShow;
    }
    final Pair<String, String> braces = (required ? MANDATORY_ARG_BRACES : OPTIONAL_ARG_BRACES);
    return String.format("%s%s%s", braces.first, textToShow, braces.second);
  }

  /**
   * Attaches argument displaying layer to console. Be sure your console has commands.
   * For now, <strong>only {@link CommandLineFile} is supported for now!</strong>
   *
   * @param console console to attach
   * @throws IllegalArgumentException is passed argument is not {@link CommandLineFile}
   */
  static void attach(@NotNull final CommandConsole console) {
    final PsiFile consoleFile = console.getFile();
    if (!(consoleFile instanceof CommandLineFile)) {
      throw new IllegalArgumentException(
        String.format("Passed argument is %s, but has to be %s", consoleFile.getClass(), CommandLineFile.class));
    }

    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final Color optionalColour = scheme.getAttributes(ConsoleHighlighter.GRAY).getForegroundColor();
    final Color requiredColour = scheme.getAttributes(TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES).getForegroundColor();

    console.getConsoleEditor().registerLineExtensionPainter(line -> {
      final PsiFile file = console.getFile();
      if (file instanceof CommandLineFile) {
        final ValidationResult result = ((CommandLineFile)file).getValidationResult();
        final Pair<Boolean, Argument> arg = result != null ? result.getNextArg() : null;
        if (arg != null) {
          final @NlsSafe String text = wrapBracesIfNeeded(arg.first, arg.second.getHelp().getHelpString());
          final TextAttributes attributes = ConsoleViewContentType.USER_INPUT.getAttributes();
          attributes.setForegroundColor(arg.first ? requiredColour : optionalColour);
          return Collections.singletonList(new LineExtensionInfo(" " + text, attributes));
        }
      }
      return Collections.emptyList();
    });
  }
}
