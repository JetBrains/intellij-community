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

import com.intellij.codeInsight.template.impl.TemplateColors;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorImpl.CaretRectangle;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiModificationTracker.Listener;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.commandInterface.command.Argument;
import com.jetbrains.commandInterface.commandLine.ValidationResult;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Displays argument hint (if required).
 * It tracks PSI changes, obtains validation info, and draws itself.
 * Will be added as {@link LanguageConsoleImpl#addLayerToPane(JComponent) layer}
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "NonSerializableFieldInSerializableClass", "DeserializableClassInSecureContext",
  "SerializableClassInSecureContext"}) // Nobody would serialize this class
final class ArgumentHintLayer extends JPanel implements Listener, Runnable { // Runnable to hide on console state changes

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

  @NotNull
  private final LanguageConsoleImpl myConsole;
  /**
   * Color to be used for required arguments
   */
  @NotNull
  private final Color myRequiredColor;
  /**
   * Color to be used for optional arguments
   */
  @NotNull
  private final Color myOptionalColor;
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
   * Current position of caret in {@link CommandConsole#getConsoleEditor()}
   */
  private int myCaretPositionPx;

  /**
   * @param console console to wrap
   */
  private ArgumentHintLayer(@NotNull final LanguageConsoleImpl console) {
    myConsole = console;
    myLastText = console.getFile().getText();
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myOptionalColor = scheme.getAttributes(ConsoleHighlighter.GRAY).getForegroundColor();
    myRequiredColor = scheme.getAttributes(TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES).getForegroundColor();
  }


  @Override
  public void modificationCountChanged() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final String newText = myConsole.getFile().getText();
    if (newText.equals(myLastText)) { // If text did not changed from last time, we do nothing
      return;
    }
    myLastText = newText; // Store text for next time check


    myNextArg = null; // Reset current argument


    final PsiFile consoleFile = myConsole.getFile();
    // Get next arg from file
    if (consoleFile instanceof CommandLineFile) {
      final ValidationResult result = ((CommandLineFile)consoleFile).getValidationResult();
      myNextArg = result != null ? result.getNextArg() : null;
      repaint(); // We need to repaint us if argument changed
    }
  }


  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    final Pair<Boolean, Argument> nextArg = myNextArg;
    if (nextArg == null) {
      return; // Nothing to show
    }
    /**
     *
     * We should display argument right after trimmed (spaces removed) document end or cursor position what ever comes first.
     *
     * We also need to add prompt length.
     */

    final EditorImpl consoleEditor = PyUtil.as(myConsole.getConsoleEditor(), EditorImpl.class);
    if (consoleEditor == null) {
      /**
       * We can't calculate anything if editor is not {@link EditorImpl})
       */
      Logger.getInstance(ArgumentHintLayer.class).warn("Bad editor: " + myConsole.getConsoleEditor());
      return;
    }
    final int consoleFontType = consoleEditor.getCaretModel().getTextAttributes().getFontType();
    final FontMetrics consoleFontMetrics = consoleEditor.getFontMetrics(consoleFontType);
    final Font consoleFont = consoleFontMetrics.getFont();

    // Copy rendering hints
    final Graphics2D sourceGraphics2 = PyUtil.as(consoleEditor.getComponent().getGraphics(), Graphics2D.class);
    if (sourceGraphics2 != null && g instanceof Graphics2D) {
      ((Graphics2D)g).setRenderingHints(sourceGraphics2.getRenderingHints());
    }

    final boolean argumentRequired = nextArg.first;
    final String argumentText = nextArg.second.getHelp().getHelpString();

    g.setFont(consoleFont);
    g.setColor(argumentRequired ? myRequiredColor : myOptionalColor);

    final String textToShow = wrapBracesIfNeeded(argumentRequired, StringUtil.isEmpty(argumentText)
                                                                   ? PyBundle.message("commandLine.argumentHint.defaultName")
                                                                   : argumentText);

    // Update caret position (if known)
    final CaretRectangle[] locations = consoleEditor.getCaretLocations(true);
    if (locations != null) {
      final CaretRectangle rectangle = locations[0];
      myCaretPositionPx = rectangle.myPoint.x;
    }


    final int consoleEditorTop = consoleEditor.getComponent().getLocation().y;
    final double textHeight = Math.floor(consoleFont.getStringBounds(textToShow, consoleFontMetrics.getFontRenderContext()).getY());

    @SuppressWarnings("NumericCastThatLosesPrecision") // pixels in position should be integer, anyway
    final int y = (int)(consoleEditorTop - textHeight);
    // We should take scrolling into account to prevent argument "flying" over text when user scrolls it, like "position:fixed" in css
    final Point scrollLocation = consoleEditor.getContentComponent().getLocation();
    final int spaceWidth = EditorUtil.getSpaceWidth(consoleFontType, consoleEditor);


    // Remove whitespaces on the end of document
    /**
     * TODO: This is actually copy/paste with {@link com.intellij.openapi.editor.actions.EditorActionUtil#moveCaretToLineEnd}.
     * Need to merge somehow.
     */
    final String trimmedDocument = StringUtil.trimTrailing(consoleEditor.getDocument().getText());
    final double trimmedDocumentWidth = consoleFont.getStringBounds(trimmedDocument, consoleFontMetrics.getFontRenderContext()).getWidth();
    @SuppressWarnings("NumericCastThatLosesPrecision") // pixels in position should be integer, anyway
    final int contentWidth = (int)Math.ceil(trimmedDocumentWidth + consoleEditor.getPrefixTextWidthInPixels());

    g.drawString(textToShow, Math.max(myCaretPositionPx, contentWidth) + scrollLocation.x + spaceWidth, y + scrollLocation.y);
  }

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

  @Override
  public void run() {
    // Console state changed! Hide...
    myNextArg = null;
    repaint();
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
    final ArgumentHintLayer argumentHintLayer = new ArgumentHintLayer(console);
    console.addStateChangeListener(argumentHintLayer);
    final MessageBusConnection connection = console.getProject().getMessageBus().connect();
    connection.subscribe(PsiModificationTracker.TOPIC, argumentHintLayer);
    console.addLayerToPane(argumentHintLayer);
    Disposer.register(console, new Disconnector(connection)); // Registered to disconnect on disposal
  }

  /**
   * Disconnects connect on {@link #dispose()}
   */
  private static final class Disconnector implements Disposable {
    @NotNull
    private final MessageBusConnection myConnection;


    /**
     * @param connection connection to disconnect on {@link #dispose()}
     */
    private Disconnector(@NotNull final MessageBusConnection connection) {
      myConnection = connection;
    }

    @Override
    public void dispose() {
      myConnection.disconnect();
    }
  }
}