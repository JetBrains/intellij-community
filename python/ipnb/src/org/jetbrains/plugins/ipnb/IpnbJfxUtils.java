package org.jetbrains.plugins.ipnb;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.MarkdownUtil;
import com.intellij.util.ui.UIUtil;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpnbJfxUtils {
  private static final Logger LOG = Logger.getInstance(IpnbJfxUtils.class);
  private static final String ourPrefix = "<html><head><script type=\"text/x-mathjax-config\">\n" +
                                          "            MathJax.Hub.Config({\n" +
                                          "                tex2jax: {\n" +
                                          "                    inlineMath: [ ['$','$'], [\"\\\\(\",\"\\\\)\"] ],\n" +
                                          "                    displayMath: [ ['$$','$$'], [\"\\\\[\",\"\\\\]\"] ],\n" +
                                          "                    processEscapes: true,\n" +
                                          "                    processEnvironments: true\n" +
                                          "                },\n" +
                                          "                displayAlign: 'center',\n" +
                                          "                \"HTML-CSS\": {\n" +
                                          "                    styles: {'#mydiv': {\"font-size\": %s}},\n" +
                                          "                    preferredFont: null,\n" +
                                          "                    linebreaks: { automatic: true }\n" +
                                          "                }\n" +
                                          "            });\n" +
                                          "</script><script type=\"text/javascript\"\n" +
                                          " src=\"http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS_HTML-full\">\n" +
                                          " </script></head><body><div id=\"mydiv\">";

  private static final String ourPostfix = "</div></body></html>";
  private static URL ourStyleUrl;

  public static JComponent createHtmlPanel(@NotNull final String source, int width) {

    final JFXPanel javafxPanel = new JFXPanel(){
      @Override
      protected void processMouseWheelEvent(MouseWheelEvent e) {
        final Container parent = getParent();
        final MouseEvent parentEvent = SwingUtilities.convertMouseEvent(this, e, parent);
        parent.dispatchEvent(parentEvent);
      }
    };
    javafxPanel.setBackground(IpnbEditorUtil.getBackground());

    Platform.runLater(() -> {
      final WebView webView = new WebView();
      webView.setOnDragDetected(event -> {
      });
      final WebEngine engine = webView.getEngine();
      initHyperlinkListener(engine);
      engine.setOnStatusChanged(status -> adjustHeight(webView, javafxPanel, source));

      final String prefix = String.format(ourPrefix, EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize() + 4);
      engine.loadContent(prefix + convertToHtml(source) + ourPostfix);
      final BorderPane pane = new BorderPane(webView);
      final Scene scene = new Scene(pane, width != 0 ? width : 20, 20);
      javafxPanel.setScene(scene);
      Platform.runLater(() -> adjustHeight(webView, javafxPanel, source));
      updateLaf(LafManager.getInstance().getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo,
                pane, engine, javafxPanel);
    });

    return javafxPanel;
  }

  private static String convertToHtml(@NotNull String source) {
    source = StringUtil.replace(source, "class=\"alert alert-success\"", "class=\"alert-success\"");
    source = StringUtil.replace(source, "class=\"alert alert-error\"", "class=\"alert-error\"");
    ArrayList<String> lines = ContainerUtil.newArrayList(source.split("\n|\r|\r\n"));

    MarkdownUtil.replaceHeaders(lines);
    source = StringUtil.join(lines, "\n");
    final StringBuilder result = new StringBuilder();

    source = replaceLinks(source);

    boolean escaped = false;
    int start = 0;
    int end = StringUtil.indexOf(source, "```");
    while (end > 0) {
      result.append(source.substring(start, end));
      result.append(escaped? "</pre>" : "<pre>");
      escaped = !escaped;
      start = end + 3;
      end = StringUtil.indexOf(source, "```", end + 1);
    }
    result.append(source.substring(start));

    return result.toString();
  }

  @NotNull
  private static String replaceLinks(@NotNull String source) {
    final Pattern inlineLink = Pattern.compile("(\\[(.*?)\\]\\([ \\t]*<?(.*?)>?[ \\t]*(([\'\"])(.*?)\\5)?\\))", Pattern.DOTALL);
    final Matcher matcher = inlineLink.matcher(source);
    final StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String linkText = matcher.group(2);
      String url = matcher.group(3);
      String title = matcher.group(6);
      StringBuilder link = new StringBuilder();
      link.append("<a href=\"").append(url).append("\"");
      if(title != null) {
        link.append(" title=\"");
        link.append(title);
        link.append("\"");
      }

      link.append(">").append(linkText);
      link.append("</a>");
      matcher.appendReplacement(sb, link.toString());
    }
    matcher.appendTail(sb);

    source = sb.toString();
    return source;
  }

  private static void initHyperlinkListener(@NotNull final WebEngine engine) {
    engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
        final EventListener listener = new HyperlinkListener(engine);
        addListenerToAllHyperlinkItems(engine, listener);
      }
    });
  }

  private static void addListenerToAllHyperlinkItems(WebEngine engine, EventListener listener) {
    final Document doc = engine.getDocument();
    if (doc != null) {
      final NodeList nodeList = doc.getElementsByTagName("a");
      for (int i = 0; i < nodeList.getLength(); i++) {
        ((EventTarget)nodeList.item(i)).addEventListener("click", listener, false);
      }
    }
  }

  private static class HyperlinkListener implements EventListener {
    @NotNull private final WebEngine myEngine;

    public HyperlinkListener(@NotNull final WebEngine engine) {
      myEngine = engine;
    }

    @Override
    public void handleEvent(Event ev) {
      String domEventType = ev.getType();
      if (domEventType.equals("click")) {
        myEngine.setJavaScriptEnabled(true);
        myEngine.getLoadWorker().cancel();
        ev.preventDefault();

        UIUtil.invokeLaterIfNeeded(() -> {

          final String href = ((Element)ev.getTarget()).getAttribute("href");
          if (href == null) return;
          final URI address;
          try {
            address = new URI(href);
            BrowserUtil.browse(address);
          }
          catch (URISyntaxException e) {
            LOG.warn(e.getMessage());
          }
        });

      }
    }
  }

  private static void adjustHeight(final WebView webView, final JFXPanel javafxPanel, String source) {
    try {
      Object result = webView.getEngine().executeScript("document.getElementById(\"mydiv\").offsetHeight");
      if (result instanceof Integer) {
        final int fontSize = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize();
        double x = (double)source.length() * 8 / (int)result;
        final double height = (source.length() * fontSize) / x + 20;
        final int width = (int)(webView.getWidth() == 0 ? 1500 : webView.getWidth());
        final Dimension size = new Dimension(width, (int)height);

        UIUtil.invokeLaterIfNeeded(() -> {
          javafxPanel.setPreferredSize(size);
          javafxPanel.revalidate();
          javafxPanel.repaint();
        });
      }
    }
    catch (JSException ignore) {
    }
  }

  private static void updateLaf(boolean isDarcula, BorderPane pane, WebEngine engine, JFXPanel jfxPanel) {
    if (isDarcula) {
      updateLafDarcula(pane, engine, jfxPanel);
    }
  }

  private static void updateLafDarcula(BorderPane pane, WebEngine engine, JFXPanel jfxPanel) {
    Platform.runLater(() -> {
      ourStyleUrl = IpnbFileType.class.getResource("/style/javaFXBrowserDarcula.css");
      engine.setUserStyleSheetLocation(ourStyleUrl.toExternalForm());
      pane.setStyle("-fx-background-color: #313335");
      jfxPanel.getScene().getStylesheets().add(ourStyleUrl.toExternalForm());
      engine.reload();
    });
  }
}
