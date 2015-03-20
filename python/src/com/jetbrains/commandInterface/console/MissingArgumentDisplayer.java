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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiModificationTracker.Listener;
import com.intellij.ui.JBColor;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.commandInterface.command.Argument;
import com.jetbrains.commandInterface.gnuCommandLine.ValidationResult;
import com.jetbrains.commandInterface.gnuCommandLine.psi.CommandLineFile;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Displays missing argument (if required).
 * It tracks PSI changes, obtains validation info, and draws itself.
 * Will be added as {@link LanguageConsoleImpl#addWaterMark(JComponent) watermark}
 *
 * @author Ilya.Kazakevich
 */
final class MissingArgumentDisplayer extends JPanel implements Listener {

  /**
   * Braces for mandatory args are [] according to GNU/POSIX recommendations
   */
  @NotNull
  private static final Pair<String, String> MANDATORY_ARG_BRACES = Pair.create("[", "]");
  /**
   * Braces for optional args are &lt;&gt; according to GNU/POSIX recommendations
   */
  @NotNull
  private static final Pair<String, String> OPTIONAL_ARG_BRACES = Pair.create("<", ">");

  /**
   * Number of places after end of line before argument place
   */
  private static final int SPACES_BEFORE_ARG = 1;
  /**
   * Width of one char (hopefully monospace)
   */
  private final int myCharWidthPx;
  /**
   * Width of prompt
   */
  private final int myPromptWidthPx;
  /**
   * Height of line (actually, one character height)
   */
  private final int myLineHeightPx;
  @NotNull
  private final LanguageConsoleImpl myConsole;
  /**
   * Number of chars in current document
   */
  private volatile int myDocumentLengthInChars;
  /**
   * Next argument to display (or null if nothing to display)
   */
  @Nullable
  private volatile Pair<Boolean, Argument> myNextArg;
  /**
   * Text console had last time (for cache purposes: no need to repaint argument if text did not changed)
   */
  @NotNull
  private volatile String myLastText = "";

  /**
   * @param console console to wrap
   */
  private MissingArgumentDisplayer(@NotNull final LanguageConsoleImpl console) {
    final FontMetrics metrics = console.getFontMetrics(console.getFont());
    myCharWidthPx = metrics.charWidth('A'); // Should be monospace
    myLineHeightPx = metrics.getHeight();
    final String prompt = console.getPrompt();
    myPromptWidthPx = (prompt != null ? prompt.length() * myCharWidthPx : 0);
    myConsole = console;
    myDocumentLengthInChars = console.getEditorDocument().getTextLength();
    myLastText = console.getFile().getText();
  }


  @Override
  public void modificationCountChanged() {
    final String newText = myConsole.getFile().getText();
    if (newText.equals(myLastText)) { // If text did not changed from last time, we do nothing
      return;
    }
    myLastText = newText; // Store text for next time check


    myNextArg = null; // Reset current argument


    myDocumentLengthInChars = newText.length();
    final PsiFile consoleFile = myConsole.getFile();
    // Get next arg from file
    if (consoleFile instanceof CommandLineFile) {
      final ValidationResult result = ((CommandLineFile)consoleFile).getValidationResult();
      if (result != null) {
        myNextArg = result.getNextArg();
        repaint(); // We need to repaint us if argument changed
      }
    }
  }


  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    // TODO: Move out this logic to prevent recalculation each swing call


    final Pair<Boolean, Argument> nextArg = myNextArg;
    if (nextArg == null) {
      return; // Nothing to show
    }


    final int y = ((int)Math.round(myConsole.getCurrentEditor().getComponent().getLocation().getY())) + myLineHeightPx;
    final int spaceToRight = myCharWidthPx * SPACES_BEFORE_ARG; // Space after line end to placeholder
    final int x = (myPromptWidthPx + (myCharWidthPx * myDocumentLengthInChars)) + spaceToRight;

    final boolean required = nextArg.first;
    final String argumentText = nextArg.second.getHelpText();
    g.setColor((required ? JBColor.red : JBColor.gray));
    final String textToShow = StringUtil.isEmpty(argumentText) ? PyBundle.message("commandLine.missingArgument.defaultName") : argumentText;
    g.drawString(wrapBracesIfNeeded(required, textToShow), x, y);
  }

  /**
   * Adds appropriate braces to argument help text if needed
   * @param required is argument required
   * @param textToShow arg help text
   * @return text to show
   */
  @NotNull
  private static String wrapBracesIfNeeded(final boolean required, @NotNull final String textToShow) {
    if (textToShow.startsWith(MANDATORY_ARG_BRACES.first) || textToShow.startsWith(OPTIONAL_ARG_BRACES.first)) {
      return textToShow;
    }
    final Pair<String, String> braces = (required ? MANDATORY_ARG_BRACES :  OPTIONAL_ARG_BRACES);
    return String.format("%s%s%s", braces.first, textToShow, braces.second);
  }


  /**
   * Attaches argument displayer to console. Be sure your console has commands
   * @param console console to attach
   */
  static void attach(@NotNull final LanguageConsoleImpl console) {
    final MissingArgumentDisplayer missingArgumentDisplayer = new MissingArgumentDisplayer(console);
    final MessageBusConnection connection = console.getProject().getMessageBus().connect();
    connection.subscribe(PsiModificationTracker.TOPIC, missingArgumentDisplayer);
    console.addWaterMark(missingArgumentDisplayer);
  }
}