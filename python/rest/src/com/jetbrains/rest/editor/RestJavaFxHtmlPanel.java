// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.ide.BrowserUtil;
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
  private volatile int myYScrollPosition = 0;
  private volatile int myXScrollPosition = 0;

  public RestJavaFxHtmlPanel() {
    super();

    runInPlatformWhenAvailable(() -> {
      final WebView webView = getWebViewGuaranteed();

      webView.getEngine().getLoadWorker().stateProperty().addListener(new HyperlinkRedirectListener(webView));

      webView.getEngine().getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
        if (newState == Worker.State.SUCCEEDED) {
          scrollTo(webView, myXScrollPosition, myYScrollPosition);
        }
      });
    });
  }

  @Override
  protected URL getStyle(boolean isDarcula) {
    return getClass().getResource(isDarcula ? "/styles/darcula.css" : "/styles/default.css");
  }

  private static class HyperlinkRedirectListener implements ChangeListener<Worker.State>{
    private static final String EVENT_TYPE_CLICK = "click";
    private final WebEngine myEngine;

    private HyperlinkRedirectListener(WebView view) {
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

  private static void scrollTo(WebView view, int x, int y) {
    view.getEngine().executeScript("window.scrollTo(" + x + ", " + y + ")");
  }

  private static int getVScrollValue(WebView view) {
    return (Integer) view.getEngine().executeScript("document.body.scrollTop");
  }

  private static int getHScrollValue(WebView view) {
    return (Integer) view.getEngine().executeScript("document.body.scrollLeft");
  }

  @Override
  public void setHtml(@NotNull String html) {
    runInPlatformWhenAvailable(() -> {
      myYScrollPosition = getVScrollValue(getWebViewGuaranteed());
      myXScrollPosition = getHScrollValue(getWebViewGuaranteed());
    });
    super.setHtml(html);
  }
}
