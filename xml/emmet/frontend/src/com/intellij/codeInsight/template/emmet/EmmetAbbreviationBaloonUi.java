// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.emmet.rpc.EmmetAbbreviationBaloonRpcFrontendHandler;
import com.intellij.codeInsight.template.emmet.rpc.ShowAbbreviationBaloonUiEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationBalloon.EmmetContextHelp.createHelpLabel;

final class EmmetAbbreviationBaloonUi {
  private static final Logger LOG = Logger.getInstance(EmmetAbbreviationBaloonUi.class);

  static void showBaloon(@NotNull ShowAbbreviationBaloonUiEvent showEvent) {
    var editor = showEvent.editor();
    if (editor == null) {
      LOG.warn("Cannot find frontend editor for the: " + showEvent);
      return;
    }
    var project = showEvent.project();
    if (project == null) {
      LOG.warn("Cannot find frontend project for the: " + showEvent);
      return;
    }
    var rpcHandler = new EmmetAbbreviationBaloonRpcFrontendHandler(project, showEvent);

    JPanel panel = new JPanel(new BorderLayout());
    final TextFieldWithStoredHistory field = new TextFieldWithStoredHistory(showEvent.getHistoryKey());
    final Dimension fieldPreferredSize = field.getPreferredSize();
    field.setPreferredSize(new Dimension(Math.max(220, fieldPreferredSize.width), fieldPreferredSize.height));
    field.setHistorySize(10);

    ContextHelpLabel label = createHelpLabel(showEvent.getLinkText(), showEvent.getLinkUrl(), showEvent.getDescription());
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
        rpcHandler.isValid((isValid) -> {
          WriteIntentReadAction.run(() -> {
            if (!isValid) {
              balloon.hide();
              return;
            }
            rpcHandler.validateTemplateKey(field, balloon, (isCorrect) -> {
            });
          });
        });
      }
    };
    field.addDocumentListener(documentListener);

    final KeyAdapter keyListener = new KeyAdapter() {
      @Override
      public void keyPressed(@NotNull KeyEvent e) {
        if (field.isPopupVisible()) {
          return;
        }
        rpcHandler.isValid((isValid) -> {
          if (!isValid) {
            balloon.hide();
            return;
          }

          switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER -> {
              rpcHandler.validateTemplateKey(field, balloon, (isCorrect) -> {
                WriteIntentReadAction.run(() -> {
                  if (!isCorrect) {
                    return;
                  }
                  final String abbreviation = field.getText();
                  rpcHandler.enter(abbreviation, () -> {
                    PropertiesComponent.getInstance().setValue(showEvent.getLastAbbreviationKey(), abbreviation);
                    field.addCurrentTextToHistory();
                    balloon.hide();
                  });
                });
              });
            }
            case KeyEvent.VK_ESCAPE -> rpcHandler.cancel(() -> balloon.hide(false));
          }
        });
      }
    };
    field.addKeyboardListener(keyListener);

    balloon.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        field.setText(PropertiesComponent.getInstance().getValue(showEvent.getLastAbbreviationKey(), ""));
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        field.removeKeyListener(keyListener);
        field.removeDocumentListener(documentListener);
      }
    });
    balloon.show(popupFactory.guessBestPopupLocation(editor), Balloon.Position.below);

    final IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
    focusManager.doWhenFocusSettlesDown(() -> {
      focusManager.requestFocus(field, true);
      field.selectText();
    });
  }
}
