// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.editor;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;

public class RestSwingHtmlPanel implements RestPreviewPanel {

  private final JTextPane myPane;
  private final JScrollPane myScrollPane;

  RestSwingHtmlPanel() {
    myPane = new JTextPane();
    myPane.setEditorKit(new HTMLEditorKit());
    myScrollPane = new JBScrollPane(myPane);
  }

  @Override
  public void setHtml(@NotNull @NlsSafe String html) {
    final int body = html.indexOf("<body>");
    if (body > 0) {
      html = "<html>" + html.substring(body);
    }
    myPane.setText(html);
  }

  @Override
  public void render() {
    myPane.revalidate();
    myPane.repaint();
  }

  @Override
  @NotNull public JComponent getComponent() {
    return myScrollPane;
  }

  @Override
  public void dispose() {
  }
}
