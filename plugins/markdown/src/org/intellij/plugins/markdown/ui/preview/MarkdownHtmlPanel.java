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

  void setHtml(@NotNull String html);

  default void setCSS(@Nullable String inlineCss, String @NotNull ... fileUris) {
  }

  default void render() {
  }

  void scrollToMarkdownSrcOffset(int offset);

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
