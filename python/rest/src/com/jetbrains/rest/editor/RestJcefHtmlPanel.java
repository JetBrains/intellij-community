// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.google.common.io.Resources;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.ui.jcef.JCEFHtmlPanel;
import com.intellij.util.ui.UIUtil;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * reStructuredText editor preview panel that uses JCEF for HTML rendering.
 *
 * @see RestPreviewFileEditor
 */
public final class RestJcefHtmlPanel extends JCEFHtmlPanel implements RestPreviewPanel {

  private static final AtomicInteger ourCounter = new AtomicInteger(-1);
  private static final @NotNull @NonNls String ourClassUrl = RestJcefHtmlPanel.class.getResource(
    RestJcefHtmlPanel.class.getSimpleName() + ".class").toExternalForm();

  private enum Style {
    DARCULA("darcula.css"),
    DEFAULT("default.css");

    @NotNull String myResource;

    Style(@NotNull String resource) {
      myResource = resource;
    }

    @NotNull String getCss() {
      return "/styles/" + myResource;
    }
  }

  private static final EnumMap<Style, String> ourLoadedStylesCache = new EnumMap<>(Style.class);

  private final JBCefJSQuery myJSQueryOpenInBrowser = JBCefJSQuery.create(this);

  private static final @Nullable String ourJSCodeToInject;

  static {
    try {
      ourJSCodeToInject = Resources.toString(Resources.getResource("/js/script.js"), StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to load script.js.", e);
    }
  }

  private final CefLoadHandler myCefLoadHandler;
  private final @NotNull Project myProject;
  private @Nullable @NonNls String myLastHtml;

  public RestJcefHtmlPanel(@NotNull Project project) {
    super(generateUniqueUrl());
    myProject = project;

    getJBCefClient().addLoadHandler(myCefLoadHandler = new CefLoadHandlerAdapter() {
      @Override
      public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        if (ourJSCodeToInject != null) {
          browser.executeJavaScript(ourJSCodeToInject, getCefBrowser().getURL(), 0);
          browser.executeJavaScript("window.__IntelliJTools.openInBrowserCallback = link => {"
                                    + myJSQueryOpenInBrowser.inject("link") + "}",
                                    getCefBrowser().getURL(), 0);
        }
      }
    }, getCefBrowser());

    myJSQueryOpenInBrowser.addHandler(link -> {
      if (!link.isEmpty()) BrowserUtil.browse(link);
      return null;
    });

    Disposer.register(this, myJSQueryOpenInBrowser);

    ApplicationManager
      .getApplication().getMessageBus().connect(this)
      .subscribe(LafManagerListener.TOPIC, source -> this.render());
  }

  @Override
  protected @NotNull String prepareHtml(@NotNull String html) {
      return html.replace("<head>", "<head>" + getCssStyleCodeToInject());
  }

  @Override
  public void setHtml(@NotNull String html) {
    String basePath = myProject.getBasePath();
    if (basePath != null)
      html = makeImageUrlsAbsolute(html, myProject.getBasePath());
    myLastHtml = html;
    super.setHtml(html);
  }

  @Override
  public void render() {
    if (myLastHtml != null) setHtml(myLastHtml);
  }

  @Override
  public void dispose() {
    super.dispose();
    getJBCefClient().removeLoadHandler(myCefLoadHandler, getCefBrowser());
  }

  private static @NotNull String generateUniqueUrl() {
    return ourClassUrl + "@" + ourCounter.incrementAndGet();
  }

  private static @NotNull String getCssStyleCodeToInject() {
    boolean isDarcula = UIUtil.isUnderDarcula();
    Style style = isDarcula ? Style.DARCULA : Style.DEFAULT;
    String cssCodeToInject = ourLoadedStylesCache.getOrDefault(style, null);
    if (cssCodeToInject != null) return cssCodeToInject;

    try {
      cssCodeToInject = Resources.toString(Resources.getResource(style.getCss()), StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to load " + style.getCss() + ".", e);
    }

    cssCodeToInject = "<style>" + cssCodeToInject + "</style>";
    ourLoadedStylesCache.put(style, cssCodeToInject);

    return cssCodeToInject;
  }

  private static @NotNull String makeImageUrlsAbsolute(@NotNull String html, @NotNull String basePath) {
    Document document = Jsoup.parse(html);
    Elements elements = document.getElementsByTag("img");

    for (Element element : elements) {
      URI uri = null;
      try {
        uri = (new URL(element.attr("src"))).toURI();
        if (!uri.getScheme().equals("file")) continue;
      }
      catch (MalformedURLException | URISyntaxException e) {
        // Assume the scheme is 'file'.
      }

      Path originalPath = Paths.get((uri != null ? uri.getPath() : element.attr("src")));
      if (originalPath.isAbsolute()) continue;

      element.attr("src", Paths.get(basePath, originalPath.toString()).toString());
    }

    return document.outerHtml();
  }
}
