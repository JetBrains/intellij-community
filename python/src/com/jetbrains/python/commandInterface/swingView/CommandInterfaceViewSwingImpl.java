/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.commandInterface.swingView;

import com.google.common.base.Preconditions;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.Balloon.Position;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Range;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.WordWithPosition;
import com.jetbrains.python.commandInterface.CommandInterfacePresenter;
import com.jetbrains.python.commandInterface.CommandInterfaceView;
import com.jetbrains.python.suggestionList.SuggestionList;
import com.jetbrains.python.suggestionList.SuggestionsBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Command-interface view implementation based on Swing.
 * It uses balloons to display errors and infos, drop-down for suggestions and also underlines errors
 *
 * @author Ilya.Kazakevich
 */
public class CommandInterfaceViewSwingImpl extends JBPopupAdapter implements CommandInterfaceView, DocumentListener, CaretListener {
  private static final JBColor ERROR_COLOR = JBColor.RED;

  /**
   * We need to track balloons, so we have field with callback
   */
  @NotNull
  private final BalloonManager myBalloonManager = new BalloonManager();
  /**
   * Pop-up we displayed in
   */
  @NotNull
  private final JBPopup myMainPopUp;
  private JPanel myPanel;
  /**
   * Upper label
   */
  private JLabel myLabel;
  /**
   * Text field
   */
  private SmartTextField myMainTextField;
  /**
   * Lower (sub) label
   */
  private JLabel mySubLabel;
  /**
   * "Suggestion area". Suggestion status is displayed when caret meets this area
   */
  private final List<Range<Integer>> myPlacesWhereSuggestionsAvailable = new ArrayList<Range<Integer>>();
  @NotNull
  private final CommandInterfacePresenter myPresenter;
  /**
   * List to display suggestions
   */
  @NotNull
  private final SuggestionList mySuggestionList;
  /**
   * Displayed when there is no text
   */
  @Nullable
  private final String myPlaceHolderText;

  /**
   * Information balloons that should be displayed when caret meets their boundaries.
   */
  @NotNull
  private final List<WordWithPosition> myInfoBalloons = new ArrayList<WordWithPosition>();
  /**
   * Error balloons that should be displayed when caret meets their boundaries.
   * Errors are always underlined, but balloons are displayed only if caret meets error
   */
  private final List<WordWithPosition> myErrorBalloons = new ArrayList<WordWithPosition>();
  /**
   * Default subtext to display when caret is out of {@link #myPlacesWhereSuggestionsAvailable "suggestion" area}
   */
  @Nullable
  private String myDefaultSubText;

  /**
   * @param presenter       our presenter
   * @param title           view title to display
   * @param project         project
   * @param placeholderText text for placeholder (to be displayed when there is not text)
   */
  public CommandInterfaceViewSwingImpl(@NotNull final CommandInterfacePresenter presenter,
                                       @NotNull final String title,
                                       @NotNull final Project project,
                                       @Nullable final String placeholderText) {
    myPresenter = presenter;
    myLabel.setText(title);
    myPlaceHolderText = placeholderText;

    myMainPopUp = JBPopupFactory.getInstance().createComponentPopupBuilder(myPanel, myMainTextField)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup();
    myMainTextField.setRequestFocusEnabled(true);
    myMainTextField.setFocusable(true);


    final int windowWidth = FileEditorManagerEx.getInstanceEx(project).getComponent().getRootPane().getWidth() - 10; // Little gap

    myMainTextField
      .setPreferredWidthInPx(windowWidth);
    mySuggestionList = new SuggestionList(new MySuggestionListListener());
  }


  @Override
  public void show() {
    myMainTextField.getDocument().addDocumentListener(this);
    myMainTextField.addCaretListener(this);
    myMainPopUp.addListener(this);
    if (myPlaceHolderText != null) {
      myMainTextField.setWaterMarkPlaceHolderText(myPlaceHolderText);
    }


    myMainTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(final FocusEvent e) {
        super.focusLost(e);
        myMainPopUp.cancel();
      }
    });
    myMainTextField.setFocusTraversalKeysEnabled(false);
    myMainTextField.addKeyListener(new MyKeyListener()); // Up/down arrows are not handles with actions

    // Register all available actions
    for (final KeyStrokeInfo strokeInfo : KeyStrokeInfo.values()) {
      strokeInfo.register(myPresenter, mySuggestionList, myMainTextField);
    }
    myMainPopUp.showInFocusCenter();
  }


  @Override
  public void displaySuggestions(@NotNull final SuggestionsBuilder suggestions, final boolean absolute, @Nullable final String toSelect) {

    int left = 0;
    // Display text right after caret
    if (!absolute) {
      left = myMainTextField.getTextCaretPositionInPx();
    }
    mySuggestionList.showSuggestions(suggestions, new RelativePoint(myPanel, new Point(left, myPanel.getHeight())), toSelect);
    configureAppropriateStatus();
  }

  @Override
  public void onClosed(final LightweightWindowEvent event) {
    super.onClosed(event);
    mySuggestionList.close();
  }


  @Override
  public void insertUpdate(final DocumentEvent e) {
    processDocumentChange();
  }


  @Override
  public void removeUpdate(final DocumentEvent e) {
    processDocumentChange();
  }

  private void processDocumentChange() {
    myMainTextField.hideUnderline();
    myPresenter.textChanged();
  }

  @Override
  public void changedUpdate(final DocumentEvent e) {

  }

  @Override
  public void removeSuggestions() {
    mySuggestionList.close();
    configureAppropriateStatus();
  }


  @Override
  public final void caretUpdate(final CaretEvent e) {


    // When caret moved, we need to check if balloon has to be displayed
    displayBalloonsIfRequired();
    configureAppropriateStatus();
  }

  private void configureAppropriateStatus() {
    if (!mySuggestionList.isClosed()) {
      // Tell user she may use TAB to complete
      mySubLabel.setText(PyBundle.message("commandLine.subText.key.complete", KeyStrokeInfo.COMPLETION.getText()));
      return;
    }

    // If we are in "suggestion available" place -- tell it
    for (final Range<Integer> range : myPlacesWhereSuggestionsAvailable) {
      final boolean specialCaseAfterLastChar = isAfterLastCharRange(range) && getCaretPosition() == myMainTextField.getText().length();
      if (range.isWithin(getCaretPosition()) || specialCaseAfterLastChar) {
        mySubLabel.setText(PyBundle.message("commandLine.subText.key.suggestions", KeyStrokeInfo.SUGGESTION.getText()));
        return;
      }
    }

    // We may simply tell user she may execute command
    if (myDefaultSubText != null) {
      mySubLabel.setText(PyBundle.message("commandLine.subText.key.executeCommand", KeyStrokeInfo.EXECUTION.getText(), myDefaultSubText));
    }
    else {
      mySubLabel.setText(PyBundle.message("commandLine.subText.key.executeUnknown", KeyStrokeInfo.EXECUTION.getText()));
    }
  }


  private void displayBalloonsIfRequired() {
    synchronized (myErrorBalloons) {
      if (mySuggestionList.isClosed()) { // No need to display error popups when suggestion list is displayed. It intersects.
        showBalloons(myErrorBalloons, Position.below, MessageType.ERROR);
      }
    }
    synchronized (myInfoBalloons) {
      showBalloons(myInfoBalloons, Position.above, MessageType.INFO);
    }
  }

  /**
   * Displays some balloons
   *
   * @param balloons      balloons to display
   * @param popUpPosition where ti display them. Only {@link Position#above} and {@link Position#below} are supported!
   * @param messageType   may be {@link MessageType#ERROR} or {@link MessageType#INFO} for example
   */
  private void showBalloons(@NotNull final List<WordWithPosition> balloons,
                            @NotNull final Position popUpPosition,
                            @NotNull final MessageType messageType) {
    Preconditions.checkArgument(popUpPosition == Position.above || popUpPosition == Position.below, "Only above or below is supported");
    for (final WordWithPosition balloon : balloons) {
      if (balloon.getText().isEmpty()) {
        continue; // Can't be displayed if empty
      }
      final int caretPosition = myMainTextField.getCaretPosition();
      if ((caretPosition >= balloon.getFrom() && caretPosition <= balloon.getTo())) {
        final int top = (popUpPosition == Position.above ? 0 : myMainTextField.getHeight() * 2); // Display below a little bit lower
        final RelativePoint point = new RelativePoint(myMainTextField, new Point(myMainTextField.getTextCaretPositionInPx(), top));
        final Balloon balloonToShow =
          JBPopupFactory.getInstance().createBalloonBuilder(new JLabel(balloon.getText())).setFillColor(messageType.getPopupBackground())
            .createBalloon();
        balloonToShow.setAnimationEnabled(false);
        myBalloonManager.registerBalloon(balloonToShow);
        balloonToShow.show(point, popUpPosition);
      }
    }
  }

  @Override
  public final void setInfoAndErrors(@NotNull final Collection<WordWithPosition> infoBalloons,
                                     @NotNull final Collection<WordWithPosition> errors) {
    synchronized (myInfoBalloons) {
      myInfoBalloons.clear();
      myInfoBalloons.addAll(infoBalloons);
    }
    synchronized (myErrorBalloons) {
      myErrorBalloons.clear();
      myErrorBalloons.addAll(errors);
    }
    for (final WordWithPosition error : errors) {
      if (isAfterLastCharRange(error)) {
        // In "special" case we use last char
        myMainTextField.underlineText(ERROR_COLOR, myMainTextField.getText().length(), myMainTextField.getText().length() + 1);
      }
      else {
        myMainTextField.underlineText(ERROR_COLOR, error.getFrom(), error.getTo());
      }
    }
  }

  /**
   * Checks if some range is <strong>special case</strong> {@link #AFTER_LAST_CHARACTER_RANGE}.
   *
   * @param range range to check
   * @return true if special case
   */
  private static boolean isAfterLastCharRange(@NotNull final Range<Integer> range) {
    return AFTER_LAST_CHARACTER_RANGE.getFrom().equals(range.getFrom()) && AFTER_LAST_CHARACTER_RANGE.getTo().equals(range.getTo());
  }

  @Override
  public final void insertTextAfterCaret(@NotNull final String text) {
    try {
      myMainTextField.getDocument().insertString(myMainTextField.getCaretPosition(), text, null);
    }
    catch (final BadLocationException e) {
      // TODO: Display error somehow!
      e.printStackTrace();
    }
  }

  @Override
  public final void replaceText(final int from, final int to, @NotNull final String newText) {
    myMainTextField.select(from, to);
    myMainTextField.replaceSelection(newText);
    myBalloonManager.closeAllBalloons();
    myPresenter.textChanged();
    displayBalloonsIfRequired(); // This crunch but we need to recalculate balloons in this case (position is changed!)
  }

  @Override
  public final int getCaretPosition() {
    return myMainTextField.getCaretPosition();
  }


  @Override
  public final void configureSubTexts(@Nullable final String defaultSubText,
                                      @NotNull final List<Range<Integer>> suggestionAvailablePlaces) {
    synchronized (myPlacesWhereSuggestionsAvailable) {
      myPlacesWhereSuggestionsAvailable.clear();
      myPlacesWhereSuggestionsAvailable.addAll(suggestionAvailablePlaces);
      myDefaultSubText = defaultSubText;
    }
    configureAppropriateStatus();
  }

  /**
   * Reacts on keys, pressed by user
   */
  private class MyKeyListener extends KeyAdapter {
    @Override
    public void keyPressed(final KeyEvent e) {
      super.keyPressed(e);

      final int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_UP) {
        mySuggestionList.moveSelection(true);
      }
      else if (keyCode == KeyEvent.VK_DOWN) {
        mySuggestionList.moveSelection(false);
      }
    }
  }

  @NotNull
  @Override
  public String getText() {
    return myMainTextField.getText();
  }


  /**
   * Listener for suggestion list
   */
  private class MySuggestionListListener extends JBPopupAdapter {

    @Override
    public void onClosed(final LightweightWindowEvent event) {
      super.onClosed(event);
      removeSuggestions();
    }
  }

  /**
   * Keeps tracks for baloons to close all of them in case of text inserion
   */
  private static final class BalloonManager extends JBPopupAdapter {
    @NotNull
    private final Set<Balloon> myCurrentBaloons = new HashSet<Balloon>();

    void registerBalloon(final Balloon balloon) {
      synchronized (myCurrentBaloons) {
        myCurrentBaloons.add(balloon);
        balloon.addListener(this);
      }
    }

    @Override
    public void onClosed(final LightweightWindowEvent event) {
      synchronized (myCurrentBaloons) {
        myCurrentBaloons.remove(event.asBalloon());
      }
      super.onClosed(event);
    }

    void closeAllBalloons() {
      synchronized (myCurrentBaloons) {
        for (final Balloon balloon : myCurrentBaloons) {
          balloon.dispose();
        }
      }
    }
  }
}
