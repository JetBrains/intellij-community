/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class EmmetAbbreviationBalloon {
  private final String myAbbreviationsHistoryKey;
  private final String myLastAbbreviationKey;
  private final Callback myCallback;
  @NotNull private final String myDocumentation;

  @Nullable
  private static String ourTestingAbbreviation;


  public EmmetAbbreviationBalloon(@NotNull String abbreviationsHistoryKey,
                                  @NotNull String lastAbbreviationKey,
                                  @NotNull Callback callback,
                                  @NotNull String documentation) {
    myAbbreviationsHistoryKey = abbreviationsHistoryKey;
    myLastAbbreviationKey = lastAbbreviationKey;
    myCallback = callback;
    myDocumentation = documentation;
  }


  @TestOnly
  public static void setTestingAbbreviation(@NotNull String testingAbbreviation, @NotNull Disposable parentDisposable) {
    ourTestingAbbreviation = testingAbbreviation;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourTestingAbbreviation = null;
      }
    });
  }

  public void show(@NotNull final CustomTemplateCallback customTemplateCallback) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (ourTestingAbbreviation == null) {
        throw new RuntimeException("Testing abbreviation is not set. See EmmetAbbreviationBalloon#setTestingAbbreviation");
      }
      myCallback.onEnter(ourTestingAbbreviation);
      return;
    }

    JPanel panel = new JPanel(new BorderLayout());
    final TextFieldWithStoredHistory field = new TextFieldWithStoredHistory(myAbbreviationsHistoryKey);
    final Dimension fieldPreferredSize = field.getPreferredSize();
    field.setPreferredSize(new Dimension(Math.max(220, fieldPreferredSize.width), fieldPreferredSize.height));
    field.setHistorySize(10);

    ContextHelpLabel label = new ContextHelpLabel(myDocumentation);
    label.setBorder(JBUI.Borders.empty(0, 3, 0, 1));

    panel.add(field, BorderLayout.CENTER);
    panel.add(label, BorderLayout.EAST);
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    final BalloonImpl balloon = (BalloonImpl)popupFactory.createBalloonBuilder(panel)
      .setCloseButtonEnabled(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(0)
      .setHideOnKeyOutside(true)
      .setHideOnClickOutside(true)
      .setFillColor(panel.getBackground())
      .createBalloon();

    final DocumentAdapter documentListener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (!isValid(customTemplateCallback)) {
          balloon.hide();
          return;
        }
        validateTemplateKey(field, balloon, field.getText(), customTemplateCallback);
      }
    };
    field.addDocumentListener(documentListener);

    final KeyAdapter keyListener = new KeyAdapter() {
      @Override
      public void keyPressed(@NotNull KeyEvent e) {
        if (!field.isPopupVisible()) {
          if (!isValid(customTemplateCallback)) {
            balloon.hide();
            return;
          }

          switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER:
              final String abbreviation = field.getText();
              if (validateTemplateKey(field, balloon, abbreviation, customTemplateCallback)) {
                myCallback.onEnter(abbreviation);
                PropertiesComponent.getInstance().setValue(myLastAbbreviationKey, abbreviation);
                field.addCurrentTextToHistory();
                balloon.hide();
              }
              break;
            case KeyEvent.VK_ESCAPE:
              balloon.hide(false);
              break;
          }
        }
      }
    };
    field.addKeyboardListener(keyListener);

    balloon.addListener(new JBPopupListener.Adapter() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
        field.setText(PropertiesComponent.getInstance().getValue(myLastAbbreviationKey, ""));
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        field.removeKeyListener(keyListener);
        field.removeDocumentListener(documentListener);
        super.onClosed(event);
      }
    });
    balloon.show(popupFactory.guessBestPopupLocation(customTemplateCallback.getEditor()), Balloon.Position.below);

    final IdeFocusManager focusManager = IdeFocusManager.getInstance(customTemplateCallback.getProject());
    focusManager.doWhenFocusSettlesDown(() -> {
      focusManager.requestFocus(field, true);
      field.selectText();
    });
  }

  private static boolean validateTemplateKey(@NotNull TextFieldWithHistory field,
                                             @Nullable Balloon balloon,
                                             @NotNull String abbreviation,
                                             @NotNull CustomTemplateCallback callback) {
    final boolean correct = ZenCodingTemplate.checkTemplateKey(abbreviation, callback);
    field.getTextEditor().setBackground(correct ? LightColors.SLIGHTLY_GREEN : LightColors.RED);
    if (balloon != null && !balloon.isDisposed()) {
      balloon.revalidate();
    }
    return correct;
  }

  private static boolean isValid(CustomTemplateCallback callback) {
    return !callback.getEditor().isDisposed();
  }

  public interface Callback {
    void onEnter(@NotNull String abbreviation);
  }
}