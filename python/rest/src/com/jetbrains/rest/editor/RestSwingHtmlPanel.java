// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;

public class RestSwingHtmlPanel implements RestPreviewPanel {

  final static NotNullLazyValue<PolicyFactory> SANITIZER_VALUE = new NotNullLazyValue<PolicyFactory>() {
    @NotNull
    @Override
    protected PolicyFactory compute() {
      return Sanitizers.BLOCKS
        .and(Sanitizers.FORMATTING)
        .and(new HtmlPolicyBuilder()
               .allowUrlProtocols("file", "http", "https").allowElements("img")
               .allowAttributes("alt", "src", "title").onElements("img")
               .allowAttributes("border", "height", "width").onElements("img")
               .toFactory())
        .and(new HtmlPolicyBuilder()
               .allowUrlProtocols("file", "http", "https", "mailto").allowElements("a")
               .allowAttributes("href", "title").onElements("a")
               .toFactory())
        .and(new HtmlPolicyBuilder()
               .allowStandardUrlProtocols()
               .allowElements("table", "tr", "td", "th", "caption", "thead", "tbody", "tfoot")
               .allowAttributes("summary").onElements("table")
               .allowAttributes("align", "valign")
               .onElements("table", "tr", "td", "th", "thead", "tbody", "tfoot")
               .allowTextIn("table")
               .toFactory())
        .and(new HtmlPolicyBuilder()
               .allowStandardUrlProtocols()
               .allowElements("pre", "span")
               .toFactory())
        .and(new HtmlPolicyBuilder()
               .allowElements("code", "tr")
               .allowAttributes("class").onElements("code", "tr")
               .toFactory());
    }
  };

  private final JTextPane myPane;
  private final JScrollPane myScrollPane;

  RestSwingHtmlPanel() {
    myPane = new JTextPane();
    myPane.setEditorKit(new HTMLEditorKit());
    myScrollPane = new JBScrollPane(myPane);
  }

  @Override
  public void setHtml(@NotNull String html) {
    html = "<html>" + SANITIZER_VALUE.getValue().sanitize(html) + "</html>";
    myPane.setText(html);
  }

  @Override
  public void render() {
    myPane.revalidate();
    myPane.repaint();
  }

  @Override
  public JComponent getComponent() {
    return myScrollPane;
  }

  @Override
  public void dispose() {
  }
}
