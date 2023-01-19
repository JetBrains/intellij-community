// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.LinkLabel;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.doIfNotNull;

public class EmmetAbbreviationBalloon {
  private final String myAbbreviationsHistoryKey;
  private final String myLastAbbreviationKey;
  private final Callback myCallback;
  @NotNull private final EmmetContextHelp myContextHelp;

  @Nullable
  private static String ourTestingAbbreviation;


  public EmmetAbbreviationBalloon(@NotNull String abbreviationsHistoryKey,
                                  @NotNull String lastAbbreviationKey,
                                  @NotNull Callback callback,
                                  @NotNull EmmetContextHelp contextHelp) {
    myAbbreviationsHistoryKey = abbreviationsHistoryKey;
    myLastAbbreviationKey = lastAbbreviationKey;
    myCallback = callback;
    myContextHelp = contextHelp;
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

    ContextHelpLabel label = myContextHelp.createHelpLabel();
    label.setBorder(JBUI.Borders.empty(0, 3, 0, 1));

    panel.add(field, BorderLayout.CENTER);
    panel.add(label, BorderLayout.EAST);
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    final Balloon balloon = popupFactory.createBalloonBuilder(panel)
      .setCloseButtonEnabled(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(0)
      .setHideOnKeyOutside(true)
      .setHideOnClickOutside(true)
      .setFillColor(panel.getBackground())
      .createBalloon();

    final DocumentAdapter documentListener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
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
            case KeyEvent.VK_ENTER -> {
              final String abbreviation = field.getText();
              if (validateTemplateKey(field, balloon, abbreviation, customTemplateCallback)) {
                myCallback.onEnter(abbreviation);
                PropertiesComponent.getInstance().setValue(myLastAbbreviationKey, abbreviation);
                field.addCurrentTextToHistory();
                balloon.hide();
              }
            }
            case KeyEvent.VK_ESCAPE -> balloon.hide(false);
          }
        }
      }
    };
    field.addKeyboardListener(keyListener);

    balloon.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        field.setText(PropertiesComponent.getInstance().getValue(myLastAbbreviationKey, ""));
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        field.removeKeyListener(keyListener);
        field.removeDocumentListener(documentListener);
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

  public static class EmmetContextHelp {
    @NotNull
    private final Supplier<@Tooltip String> myDescription;

    @Nullable
    private Supplier<@LinkLabel String> myLinkText = null;

    @Nullable
    private String myLinkUrl = null;

    public EmmetContextHelp(@NotNull Supplier<@Tooltip String> description) {
      myDescription = description;
    }

    public EmmetContextHelp(@NotNull Supplier<@Tooltip String> description,
                            @NotNull Supplier<@LinkLabel String> linkText,
                            @NotNull String linkUrl) {
      myDescription = description;
      myLinkText = linkText;
      myLinkUrl = linkUrl;
    }

    @NotNull
    public ContextHelpLabel createHelpLabel() {
      String linkText = doIfNotNull(myLinkText, Supplier::get);
      String description = myDescription.get();
      if (StringUtil.isEmpty(linkText) || StringUtil.isEmpty(myLinkUrl)) {
        return ContextHelpLabel.create(description);
      }
      return ContextHelpLabel.createWithLink(null, description, linkText, () -> BrowserUtil.browse(myLinkUrl));
    }
  }

  public interface Callback {
    void onEnter(@NotNull String abbreviation);
  }
}