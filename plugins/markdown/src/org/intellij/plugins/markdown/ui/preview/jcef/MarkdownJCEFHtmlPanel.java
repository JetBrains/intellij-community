// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.ui.jcef.JBCefPsiNavigationUtils;
import com.intellij.ui.jcef.JCEFHtmlPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.plugins.markdown.ui.preview.MarkdownAccessor;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.intellij.plugins.markdown.ui.preview.PreviewColorThemeStyles;
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class MarkdownJCEFHtmlPanel extends JCEFHtmlPanel implements MarkdownHtmlPanel {

  private final JBCefJSQuery myJSQuerySetScrollY = JBCefJSQuery.create(this);
  private final JBCefJSQuery myJSQueryOpenInBrowser = JBCefJSQuery.create(this);

  private static final NotNullLazyValue<String> MY_SCRIPTING_LINES = new NotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      return SCRIPTS.stream()
        .map(s -> "<script src=\"" + PreviewStaticServer.getScriptUrl(s) + "\"></script>")
        .reduce((s, s2) -> s + "\n" + s2)
        .orElseGet(String::new);
    }
  };

  private String @NotNull [] myCssUris = ArrayUtil.EMPTY_STRING_ARRAY;
  @NotNull
  private String myCSP = "";
  @NotNull
  private String myLastRawHtml = "";
  @NotNull
  private final ScrollPreservingListener myScrollPreservingListener = new ScrollPreservingListener();
  @NotNull
  private final BridgeSettingListener myBridgeSettingListener = new BridgeSettingListener();

  private final CefLoadHandler myCefLoadHandler;

  @NotNull
  private static final String ourClassUrl;

  static {
    String url = "about:blank";
    try {
      url = MarkdownJCEFHtmlPanel.class.getResource(MarkdownJCEFHtmlPanel.class.getSimpleName() + ".class").toExternalForm();
    }
    catch (Exception ignored) {
    }
    ourClassUrl = url;
  }

  public MarkdownJCEFHtmlPanel() {
    super(ourClassUrl + "@" + new Random().nextInt(Integer.MAX_VALUE));

    myJSQuerySetScrollY.addHandler((scrollY) -> {
      try {
        myScrollPreservingListener.myScrollY = Integer.parseInt(scrollY);
      }
      catch (NumberFormatException ignored) {
      }
      return null;
    });

    if (Registry.is("markdown.open.link.in.external.browser")) {
      myJSQueryOpenInBrowser.addHandler((link) -> {
        if (JBCefPsiNavigationUtils.INSTANCE.navigateTo(link)) return null;
        MarkdownAccessor.getSafeOpenerAccessor().openLink(link);
        return null;
      });
    }

    getJBCefClient().addLoadHandler(myCefLoadHandler = new CefLoadHandlerAdapter() {
      @Override
      public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        myScrollPreservingListener.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
        myBridgeSettingListener.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
      }
    }, getCefBrowser());
  }

  @Override
  public void setHtml(@NotNull String html) {
    myLastRawHtml = html;
    super.setHtml(html);
  }

  @NotNull
  @Override
  protected String prepareHtml(@NotNull String html) {
    return MarkdownAccessor.getImageRefreshFixAccessor().setStamps(
      html
        .replace("<head>", "<head>"
                           +
                           "<meta http-equiv=\"Content-Security-Policy\" content=\"" +
                           myCSP +
                           "\"/>"
                           +
                           MarkdownHtmlPanel.getCssLines(null, myCssUris) +
                           "\n" +
                           getScriptingLines()));
  }

  @Override
  public void setCSS(@Nullable String inlineCss, String @NotNull ... fileUris) {
    PreviewStaticServer.getInstance().setInlineStyle(inlineCss);
    PreviewStaticServer.getInstance().setColorThemeStyles(PreviewColorThemeStyles.createStylesheet());
    String[] baseStyles =
      ArrayUtil.mergeArrays(fileUris, PreviewStaticServer.getStyleUrl(PreviewStaticServer.COLOR_THEME_CSS_FILENAME));
    myCssUris = inlineCss == null ? baseStyles
                                  : ArrayUtil
                  .mergeArrays(baseStyles, PreviewStaticServer.getStyleUrl(PreviewStaticServer.INLINE_CSS_FILENAME));
    myCSP = PreviewStaticServer.createCSP(ContainerUtil.map(SCRIPTS, s -> PreviewStaticServer.getScriptUrl(s)),
                                          ContainerUtil.concat(
                                            ContainerUtil.map(STYLES, s -> PreviewStaticServer.getStyleUrl(s)),
                                            ContainerUtil.filter(fileUris, s -> s.startsWith("http://") || s.startsWith("https://"))
                                          ));
    setHtml(myLastRawHtml);
  }

  @Override
  public void render() {
  }

  @Override
  public void scrollToMarkdownSrcOffset(final int offset) {
    getCefBrowser().executeJavaScript(
      "if ('__IntelliJTools' in window) " +
      "__IntelliJTools.scrollToOffset(" + offset + ", '" + HtmlGenerator.Companion.getSRC_ATTRIBUTE_NAME() + "');",
      getCefBrowser().getURL(), 0);

    getCefBrowser().executeJavaScript(
      "var value = document.documentElement.scrollTop || (document.body && document.body.scrollTop);" +
      myJSQuerySetScrollY.inject("value"),
      getCefBrowser().getURL(), 0);
  }

  @Override
  public void dispose() {
    super.dispose();
    getJBCefClient().removeLoadHandler(myCefLoadHandler, getCefBrowser());
    Disposer.dispose(myJSQuerySetScrollY);
    Disposer.dispose(myJSQueryOpenInBrowser);
  }

  @NotNull
  private static String getScriptingLines() {
    return MY_SCRIPTING_LINES.getValue();
  }

  private class BridgeSettingListener extends CefLoadHandlerAdapter {
    @Override
    public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
      if (Registry.is("markdown.open.link.in.external.browser")) {
        getCefBrowser().executeJavaScript(
          "window.JavaPanelBridge = {" +
          "openInExternalBrowser : function(link) {" +
          myJSQueryOpenInBrowser.inject("link") +
          "}" +
          "};",
          getCefBrowser().getURL(), 0);
      }
    }
  }

  private class ScrollPreservingListener extends CefLoadHandlerAdapter {
    volatile int myScrollY = 0;

    @Override
    public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
      if (isLoading) {
        getCefBrowser().executeJavaScript(
          "var value = document.documentElement.scrollTop || document.body.scrollTop;" +
          myJSQuerySetScrollY.inject("value"),
          getCefBrowser().getURL(), 0);
      }
      else {
        getCefBrowser().executeJavaScript("document.documentElement.scrollTop = ({} || document.body).scrollTop = " + myScrollY,
                                          getCefBrowser().getURL(), 0);
      }
    }
  }
}
