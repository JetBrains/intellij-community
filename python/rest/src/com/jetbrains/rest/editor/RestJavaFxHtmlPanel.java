// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.ui.javafx.JavaFxHtmlPanel;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.web.WebView;

import java.net.URL;

public class RestJavaFxHtmlPanel extends JavaFxHtmlPanel implements RestPreviewPanel {
  public RestJavaFxHtmlPanel() {
    super();
    LafManager.getInstance().addLafManagerListener(new RestLafManagerListener());

    runInPlatformWhenAvailable(() -> {
      final WebView webView = getWebViewGuaranteed();
      webView.fontSmoothingTypeProperty().setValue(FontSmoothingType.LCD);
      updateLaf(LafManager.getInstance().getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo);
    });
  }

  private class RestLafManagerListener implements LafManagerListener {
    @Override
    public void lookAndFeelChanged(LafManager manager) {
      updateLaf(manager.getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo);
    }
  }

  private URL getStyle(boolean isDarcula) {
    return getClass().getResource(isDarcula ? "/styles/darcula.css" : "/styles/default.css");
  }

  private void updateLaf(boolean isDarcula) {
    runInPlatformWhenAvailable(() -> {
      final WebView webView = getWebViewGuaranteed();
      webView.getEngine().setUserStyleSheetLocation(getStyle(isDarcula).toExternalForm());
    });
  }
}
