// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.javafx.JavaFxHtmlPanel;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.html.HTMLAnchorElement;

import java.net.URL;

public class RestJavaFxHtmlPanel extends JavaFxHtmlPanel implements RestPreviewPanel {
  public RestJavaFxHtmlPanel() {
    super();
    LafManager.getInstance().addLafManagerListener(new RestLafManagerListener());

    runInPlatformWhenAvailable(() -> {
      final WebView webView = getWebViewGuaranteed();
      webView.fontSmoothingTypeProperty().setValue(FontSmoothingType.LCD);

      webView.getEngine().getLoadWorker().stateProperty().addListener(new HyperlinkRedirectListener(webView));
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
    ApplicationManager.getApplication().invokeLater(() ->
      runInPlatformWhenAvailable(() -> {
        final WebView webView = getWebViewGuaranteed();
        webView.getEngine().setUserStyleSheetLocation(getStyle(isDarcula).toExternalForm());
      }
    ));
  }

  private static class HyperlinkRedirectListener implements ChangeListener<Worker.State>{
    private static final String EVENT_TYPE_CLICK = "click";
    private final WebEngine myEngine;

    public HyperlinkRedirectListener(WebView view) {
      myEngine = view.getEngine();
    }

    @Override
    public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
      if (newValue == Worker.State.SUCCEEDED) {
        final EventListener listener = makeHyperLinkListener();
        addListenerToAllHyperlinkItems(listener);
      }
    }
    private void addListenerToAllHyperlinkItems(EventListener listener) {
      final Document doc = myEngine.getDocument();
      if (doc != null) {
        final NodeList nodeList = doc.getElementsByTagName("a");
        for (int i = 0; i < nodeList.getLength(); i++) {
          ((EventTarget)nodeList.item(i)).addEventListener(EVENT_TYPE_CLICK, listener, false);
        }
      }
    }

    @NotNull
    private static EventListener makeHyperLinkListener() {
      return new EventListener() {
        @Override
        public void handleEvent(Event ev) {
          EventTarget target = ev.getCurrentTarget();
          HTMLAnchorElement anchorElement = (HTMLAnchorElement) target;
          String href = anchorElement.getHref();
          if (href == null) return;
          BrowserUtil.browse(href);
          ev.preventDefault();
        }
      };
    }
  }
}
