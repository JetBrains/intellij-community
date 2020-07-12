package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Range;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.plugins.markdown.settings.MarkdownCssSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Node;

import javax.swing.*;
import java.util.List;

public interface MarkdownHtmlPanel extends Disposable {
  List<String> SCRIPTS = ContainerUtil.immutableList("processLinks.js", "scrollToElement.js");

  List<String> STYLES = ContainerUtil.immutableList(
    "default.css",
    PreviewStaticServer.COLOR_THEME_CSS_FILENAME,
    PreviewStaticServer.INLINE_CSS_FILENAME
  );

  @NotNull
  JComponent getComponent();

  void setHtml(@NotNull String html);

  void setCSS(@Nullable String inlineCss, String @NotNull ... fileUris);

  void render();

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

  @NotNull
  static String getCssLines(@Nullable String inlineCss, String @NotNull ... fileUris) {
    StringBuilder result = new StringBuilder();

    for (String uri : fileUris) {
      uri = migrateUriToHttp(uri);
      result.append("<link rel=\"stylesheet\" href=\"").append(uri).append("\" />\n");
    }
    if (inlineCss != null) {
      result.append("<style>\n").append(inlineCss).append("\n</style>\n");
    }
    return result.toString();
  }

  static String migrateUriToHttp(@NotNull String uri) {
    if (uri.equals(MarkdownCssSettings.DEFAULT.getStylesheetUri())) {
      return PreviewStaticServer.getStyleUrl("default.css");
    }
    else {
      return uri;
    }
  }
}
