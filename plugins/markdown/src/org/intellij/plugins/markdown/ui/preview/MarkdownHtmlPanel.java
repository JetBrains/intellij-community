// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Range;
import org.intellij.markdown.html.HtmlGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Node;

import javax.swing.*;
import java.util.List;

public interface MarkdownHtmlPanel extends Disposable {
  @NotNull
  JComponent getComponent();

  void setHtml(@NotNull String html, int initialScrollOffset);

  default void reloadWithOffset(int offset) {}

  @Deprecated
  default void setCSS(@Nullable String inlineCss, String @NotNull ... fileUris) {
  }

  @Deprecated
  default void render() {
  }

  void scrollToMarkdownSrcOffset(int offset);

  interface ScrollListener {
    void onScroll(int offset);
  }

  default void addScrollListener(ScrollListener listener) {}

  default void removeScrollListener(ScrollListener listener) {}

  @Nullable
  static Range<Integer> nodeToSrcRange(@NotNull Node node) {
    if (!node.hasAttributes()) {
      return null;
    }
    final Node attribute = node.getAttributes().getNamedItem(HtmlGenerator.Companion.getSRC_ATTRIBUTE_NAME());
    if (attribute == null) {
      return null;
    }
    final List<String> startEnd = StringUtil.split(attribute.getNodeValue(), "..");
    if (startEnd.size() != 2) {
      return null;
    }
    return new Range<>(Integer.parseInt(startEnd.get(0)), Integer.parseInt(startEnd.get(1)));
  }
}
