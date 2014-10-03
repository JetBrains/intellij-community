package org.jetbrains.plugins.ipnb;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.MarkdownUtil;
import com.petebevin.markdown.MarkdownProcessor;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class IpnbUtils {
  private static final Logger LOG = Logger.getInstance(IpnbUtils.class);
  private static MarkdownProcessor ourMarkdownProcessor = new MarkdownProcessor();

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
                                          "                    styles: {'.MathJax_Display': {\"margin\": 0}},\n" +
                                          "                    webFont: null,\n" +
                                          "                    preferredFont: null,\n" +
                                          "                    linebreaks: { automatic: true }\n" +
                                          "                }\n" +
                                          "            });\n" +
                                          "</script><script type=\"text/javascript\"\n" +
                                          "  src=\"http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML\">\n" +
                                          "</script></head><body style='width: " + IpnbEditorUtil.PANEL_WIDTH + "px'><div id=\"mydiv\">";

  public static String markdown2Html(@NotNull final String description) {
    // TODO: add links to the dependant notebook pages (see: index.ipynb)
    //TODO: relative picture links (see: index.ipynb in IPython.kernel) We should use absolute file:/// path
    final List<String> lines = ContainerUtil.newArrayList(description.split("\n|\r|\r\n"));
    final List<String> processedLines = new ArrayList<String>();
    boolean isInCode = false;
    for (String line : lines) {
      String processedLine = line;
      if (line.startsWith(" ")) {
        processedLine = line.substring(1);
      }

      if (processedLine.contains("```")) isInCode = !isInCode;
      if (isInCode) {
        processedLine = processedLine
          .replace("&", "&amp;");
      }
      else {
        processedLine = processedLine
          .replaceAll("([\\w])_([\\w])", "$1&underline;$2");
      }
      processedLines.add(processedLine);
    }
    MarkdownUtil.replaceHeaders(processedLines);
    MarkdownUtil.removeImages(processedLines);
    MarkdownUtil.generateLists(processedLines);
    MarkdownUtil.replaceCodeBlock(processedLines);
    final String[] lineArray = ArrayUtil.toStringArray(processedLines);
    final String normalizedMarkdown = StringUtil.join(lineArray, "\n");
    String html = ourMarkdownProcessor.markdown(normalizedMarkdown);
    html = html
      .replace("<pre><code>", "<pre>").replace("</code></pre>", "</pre>")
      .replace("<em>", "<i>").replace("</em>", "</i>")
      .replace("<strong>", "<b>").replace("</strong>", "</b>")
      .replace("&underline;", "_")
      .trim();
    return html;
  }

  public static void addLatexToPanel(@NotNull final String source, @NotNull final JPanel panel) {
    final StringBuilder result = convertToHtml(source);
    addToPanel(result.toString(), panel);

  }

  private static StringBuilder convertToHtml(@NotNull final String source) {
    final StringBuilder result = new StringBuilder();
    StringBuilder markdown = new StringBuilder();
    boolean inCode = false;
    int inMultiStringCode = 0;
    boolean inEnd = false;
    boolean escaped = false;
    boolean backQuoted = false;

    for (int i = 0; i != source.length(); ++i) {
      final char charAt = source.charAt(i);

      if (charAt == '`') {
        backQuoted = !backQuoted;
        if (source.length() > i + 2 && source.charAt(i + 1) == '`' && source.charAt(i + 2) == '`') {
          markdown.append(escaped ? "</pre>" : "<pre>");
          escaped = !escaped;
          i = i + 2;
          continue;
        }
      }

      if (!escaped && !backQuoted) {
        if (charAt == '$' && source.length() > i + 1 && source.charAt(i+1) != '$') {
          inCode = !inCode;
        }

        if (charAt == '\\' && source.substring(i).startsWith("\\begin")) {
          inMultiStringCode += 1;
        }
        if (charAt == '\\' && source.substring(i).startsWith("\\end")) {
          inEnd = true;
        }
      }

      final boolean doubleDollar = charAt == '$' && ((source.length() > i + 1 && source.charAt(i + 1) == '$')
                                                     || (i >= 1  && source.charAt(i - 1) == '$'));
      if (inCode || inMultiStringCode != 0 || (!backQuoted && doubleDollar)) {
        if (markdown.length() != 0) {
          result.append(markdown2Html(markdown.toString()));
          markdown = new StringBuilder();
        }

        result.append(charAt);
      }
      else {
        markdown.append(charAt);
      }

      if (inEnd && charAt == '}') {
        inMultiStringCode -= 1;
      }

    }
    if (markdown.length() != 0) {
      result.append(markdown2Html(markdown.toString()));
    }
    return result;
  }

  public static void addToPanel(@NotNull final String source, @NotNull final JPanel panel) {
    Platform.setImplicitExit(false);

    final String text = ourPrefix + source + "</div></body></html>";

    final JFXPanel javafxPanel = new JFXPanel();
    javafxPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final MouseEvent parentEvent = SwingUtilities.convertMouseEvent(javafxPanel, e, panel);
        panel.dispatchEvent(parentEvent);
      }
    });
    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        final BorderPane borderPane = new BorderPane();
        final WebView webComponent = new WebView();
        webComponent.setPrefWidth(IpnbEditorUtil.PANEL_WIDTH + 100);
        webComponent.setPrefHeight(5);
        final WebEngine engine = webComponent.getEngine();
        engine.locationProperty().addListener(new ChangeListener<String>() {
          @Override
          public void changed(ObservableValue<? extends String> value, String newValue, String t1) {
            try {
              final URI address = new URI(value.getValue());
              BrowserUtil.browse(address);
            }
            catch (URISyntaxException e) {
              LOG.warn(e);
            }
          }
        });
        engine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
          @Override
          public void changed(ObservableValue<? extends Worker.State> arg0, Worker.State oldState, Worker.State newState) {
            if (newState == Worker.State.SUCCEEDED) {
              adjustHeight(webComponent, javafxPanel, panel);
            }
          }
        });
        engine.loadContent(text);
        borderPane.setCenter(webComponent);
        final Scene scene = new Scene(borderPane);
        javafxPanel.setScene(scene);
      }
    });
    panel.add(javafxPanel);
  }

  private static void adjustHeight(final WebView webComponent, final JFXPanel javafxPanel, final JPanel panel) {
    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        try {
          Object result = webComponent.getEngine().executeScript("document.getElementById(\"mydiv\").offsetHeight");
          if (result instanceof Integer) {
            final double value = ((Integer)result + 150);

            javafxPanel.setPreferredSize(new Dimension((int)webComponent.getPrefWidth(), (int)value));
            panel.revalidate();
            panel.repaint();
          }
        }
        catch (JSException e) {
          LOG.warn(e.getMessage());
        }
      }
    });
  }
}
