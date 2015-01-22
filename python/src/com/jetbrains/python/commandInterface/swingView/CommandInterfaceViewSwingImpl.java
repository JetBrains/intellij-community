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

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon.Position;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.awt.RelativePoint;
import com.jetbrains.python.commandInterface.CommandInterfacePresenter;
import com.jetbrains.python.commandInterface.CommandInterfaceView;
import com.jetbrains.python.suggestionList.SuggestionList;
import com.jetbrains.python.suggestionList.SuggestionsBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;

/**
 * Command-interface view implementation based on Swing
 *
 * @author Ilya.Kazakevich
 */
public class CommandInterfaceViewSwingImpl extends JBPopupAdapter implements CommandInterfaceView, DocumentListener {
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
  @NotNull
  private final CommandInterfacePresenter myPresenter;
  /**
   * List to display suggestions
   */
  @NotNull
  private final SuggestionList myList;
  /**
   * Displayed when there is no text
   */
  @Nullable
  private final String myPlaceHolderText;
  /**
   * Flag that indicates we are in "test forced" mode: current text set by presenter, not by user
   */
  private boolean myInForcedTextMode;

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


    final EditorWindow window = FileEditorManagerEx.getInstanceEx(project).getCurrentWindow();
    final int windowSize;
    if (window != null) {
      windowSize = window.getSize().width;
    }
    else {
      windowSize = 0; // Windows size is unknown
    }

    myMainTextField
      .setPreferredWidthInPx(windowSize);
    myList = new SuggestionList(new MySuggestionListListener());
  }


  @Override
  public void show() {
    myMainTextField.getDocument().addDocumentListener(this);

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
    myMainTextField.addKeyListener(new MyKeyListener());
    myMainPopUp.showInFocusCenter();
  }


  @Override
  public void displaySuggestions(@NotNull final SuggestionsBuilder suggestions, final boolean absolute, @Nullable final String toSelect) {

    int left = 0;
    // Display text right after line ends if not in "absolute" mode
    if (!absolute) {
      left = myMainTextField.getTextEndPosition();
    }
    myList.showSuggestions(suggestions, new RelativePoint(myPanel, new Point(left, myPanel.getHeight())), toSelect);
  }


  @Override
  public void onClosed(final LightweightWindowEvent event) {
    super.onClosed(event);
    myList.close();
  }

  @Override
  public void showError(final boolean lastOnly) {
    myMainTextField.underlineText(Color.RED, lastOnly);
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
    myPresenter.textChanged(myInForcedTextMode);
  }

  @Override
  public void changedUpdate(final DocumentEvent e) {

  }

  @Override
  public void forceText(@NotNull final String newText) {
    myInForcedTextMode = true;
    myMainTextField.setText(newText);
    myInForcedTextMode = false;
  }

  @Override
  public void removeSuggestions() {
    myList.close();
  }

  @Override
  public void displayInfoBaloon(@NotNull final String message) {
    final RelativePoint point = new RelativePoint(myMainTextField, new Point(myMainTextField.getTextEndPosition(), 0));
    JBPopupFactory.getInstance().createBalloonBuilder(new JLabel(message)).createBalloon().show(point, Position.above);
  }

  @Override
  public void setSubText(@NotNull final String subText) {
    mySubLabel.setText(subText);
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
        myList.moveSelection(true);
      }
      else if (keyCode == KeyEvent.VK_DOWN) {
        myList.moveSelection(false);
      }
      else if (keyCode == KeyEvent.VK_ENTER) {
        myPresenter.executionRequested(myList.getValue());
      }
      else if (keyCode == KeyEvent.VK_TAB) {
        myPresenter.completionRequested(myList.getValue());
      }
      else if ((keyCode == KeyEvent.VK_SPACE) && (e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK)) {
        myPresenter.suggestionRequested();
      }
    }
  }

  @NotNull
  @Override
  public String getText() {
    return myMainTextField.getText();
  }

  @Override
  public void setPreferredWidthInChars(final int widthInChars) {
    myMainTextField.setPreferredWidthInChars(widthInChars);
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
}
