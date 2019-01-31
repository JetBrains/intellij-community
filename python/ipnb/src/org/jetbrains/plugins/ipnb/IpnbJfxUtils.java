package org.jetbrains.plugins.ipnb;

import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Processor;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonHelpersLocator;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.NotNull;
import org.markdown4j.*;
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

public class IpnbJfxUtils {
  private static final Logger LOG = Logger.getInstance(IpnbJfxUtils.class);
  private static final String ourStyle = "<html><head><style>#mydiv\n{\n" +
                                         "min-width: %spx;\n" +
                                         "}</style>";
  private static final String ourBody = "</head><body><div id=\"mydiv\">";
  private static final String ourMathJaxPrefix = ourStyle +
                                                 "<script type=\"text/x-mathjax-config\">\n" +
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
                                                 " src=\" %s" +
                                                 "?config=TeX-AMS_HTML-full\">\n" +
                                                 " </script>" + ourBody;
  private static final String ourPrefix = ourStyle + ourBody;
  private static final String ourPostfix = "</div></body></html>";
  private static URL ourStyleUrl;
  private static final int renderingDelay = 10;


  private static void runFX(@NotNull Runnable r) {
    IdeEventQueue.unsafeNonblockingExecute(r);
  }

  public static JComponent createHtmlPanel(@NotNull final String source, int width) {
    final JFXPanel javafxPanel = new JFXPanel() {
      @Override
      protected void processMouseWheelEvent(MouseWheelEvent e) {
        final Container parent = getParent();
        final MouseEvent parentEvent = SwingUtilities.convertMouseEvent(this, e, parent);
        parent.dispatchEvent(parentEvent);
      }
    };
    ApplicationManager.getApplication().invokeLater(() -> runFX(() -> Platform.runLater(() -> {
      final WebView webView = new WebView();
      webView.setContextMenuEnabled(false);
      webView.setOnDragDetected(event -> {
      });
      final WebEngine engine = webView.getEngine();
      initHyperlinkListener(engine);

      final boolean hasMath = source.contains("$");
      if (hasMath) {
        engine.setOnStatusChanged(event -> {
          final String data = event.getData();
          if (data != null && data.isEmpty()) {
            adjustHeight(webView, javafxPanel, source);
          }
        });
      }
      else {
        engine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
          if (newValue == Worker.State.SUCCEEDED) {
            adjustHeight(webView, javafxPanel, source);
          }
        });
      }
      final String prefix;
      if (hasMath) {
        prefix = String.format(ourMathJaxPrefix, width - 500, EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize(),
                               PythonHelpersLocator.getHelperFile("/MathJax/MathJax.js").toURI());
      }
      else {
        prefix = String.format(ourPrefix, width - 500);
      }
      final String content = prefix + convertToHtml(source) + ourPostfix;
      engine.loadContent(content);

      final Scene scene = new Scene(webView, 0, 0);

      javafxPanel.setScene(scene);
      updateLaf(LafManager.getInstance().getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo,
                engine, javafxPanel);
    })));

    return javafxPanel;
  }

  private static String convertToHtml(@NotNull String source) {
    if (source.trim().startsWith("<iframe")) return source;
    final String result = wrapMath(source);

    final ExtDecorator decorator = new ExtDecorator();
    final Configuration.Builder builder = Configuration.builder().forceExtentedProfile()
      .registerPlugins(new YumlPlugin(), new WebSequencePlugin(), new IncludePlugin()).setDecorator(decorator)
      .setCodeBlockEmitter(new CodeBlockEmitter());
    String processed = Processor.process(result, builder.build());
    processed = unwrapMath(processed);

    return processed;
  }

  private static String unwrapMath(@NotNull String processed) {
    processed = processed.replaceAll("<code>\\$\\$", "\\$\\$");
    processed = processed.replaceAll("\\$\\$</code>", "\\$\\$");
    processed = processed.replaceAll("\\$</code>", "\\$");
    processed = processed.replaceAll("<code>\\$", "\\$");
    return processed;
  }

  @NotNull
  private static String wrapMath(@NotNull final String source) {
    final StringBuilder result = new StringBuilder();
    boolean inMath = false;
    int start = 0;
    boolean single;
    int end = StringUtil.indexOf(source, "$");
    single = end + 1 >= source.length() || source.charAt(end + 1) != '$';
    while (end >= 0) {
      String substring = source.substring(start, end);
      if (start != 0) {
        result.append(escapeMath(inMath, single));
      }
      result.append(substring);

      inMath = !inMath;
      single = end + 1 >= source.length() || source.charAt(end + 1) != '$';
      start = end + (single ? 1 : 2);
      end = StringUtil.indexOf(source, "$", start);
    }
    if (start != 0) {
      result.append(escapeMath(inMath, single));
    }

    String substring = source.substring(start);
    result.append(substring);
    return result.toString();
  }

  private static String escapeMath(boolean inMath, boolean single) {
    if (single) {
      return inMath ? "`$" : "$`";
    }
    else {
      return inMath ? "`$$" : "$$`";
    }
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

    HyperlinkListener(@NotNull final WebEngine engine) {
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
    final WebEngine engine = webView.getEngine();
    final Document document = engine.getDocument();
    if (document != null) {
      final Element mydiv = document.getElementById("mydiv");
      if (mydiv != null) {
        try {
          Thread.sleep(renderingDelay);
        }
        catch (InterruptedException ignored) {
        }
        final Object heightObject = engine.executeScript("document.height");
        final int height = heightObject instanceof Integer ? (int)heightObject : 0;
        final Object widthObject = engine.executeScript("document.width");
        int width = widthObject instanceof Integer ? (int)widthObject : 0;
        if (width < javafxPanel.getWidth()) width = javafxPanel.getWidth();
        if (height <= 0 || width <= 0) return;
        int count = countNewLinesInMath(source);

        final Dimension size = new Dimension(width, height + count * EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize());

        ApplicationManager.getApplication().invokeLater(()->{
          javafxPanel.setPreferredSize(size);
          javafxPanel.revalidate();
          javafxPanel.repaint();
        });
      }
    }
  }

  private static int countNewLinesInMath(String source) {
    int count = 0;
    if (source.contains("```")) {
      count += 1;
    }
    boolean inMath = false;

    if (source.contains("\\frac")) {
      count += 1;
    }
    if (source.contains("\\limits")) {
      count += 2;
    }
    while (source.contains("$$")) {
      if (inMath) {
        final String substring = source.substring(0, source.indexOf("$$") + 2);
        count += StringUtil.countNewLines(substring);
        for (int i = 0, len = substring.length(); i < len; ++i) {
          if (substring.charAt(i) == '\\' && i + 1 < substring.length() && substring.charAt(i + 1) == '\\') {
            count++;
            i += 1;
          }
        }
      }
      inMath = !inMath;
      source = source.substring(source.indexOf("$$") + 2);
    }
    return count;
  }

  private static void updateLaf(boolean isDarcula, WebEngine engine, JFXPanel jfxPanel) {
    if (isDarcula) {
      updateLafDarcula(engine, jfxPanel);
    }
  }

  private static void updateLafDarcula(WebEngine engine, JFXPanel jfxPanel) {
    ApplicationManager.getApplication().invokeLater(() -> runFX(() -> Platform.runLater(() -> {
      ourStyleUrl = IpnbFileType.class.getResource("/style/javaFXBrowserDarcula.css");
      engine.setUserStyleSheetLocation(ourStyleUrl.toExternalForm());
      jfxPanel.getScene().getStylesheets().add(ourStyleUrl.toExternalForm());
      engine.reload();
    })));
  }
}
