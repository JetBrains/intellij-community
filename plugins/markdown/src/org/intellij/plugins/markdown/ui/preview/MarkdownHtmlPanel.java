// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.EventListener;

public interface MarkdownHtmlPanel extends Disposable {
  @NotNull JComponent getComponent();

  /**
   * @deprecated Use {@link #setHtml(String, int, Path)} instead.
   */
  @Deprecated
  default void setHtml(@NotNull String html, int initialScrollOffset) {
    setHtml(html, initialScrollOffset, null);
  }

  /**
   * Updates current HTML content with the new one.
   * @param html new HTML content.
   * @param initialScrollOffset Offset in the original document which will be used to initially position preview content.
   * @param documentPath Path to original document. It will be used to resolve resources with relative paths, like images.
   */
  void setHtml(@NotNull String html, int initialScrollOffset, @Nullable Path documentPath);

  void reloadWithOffset(int offset);

  void scrollToMarkdownSrcOffset(int offset, boolean smooth);

  interface ScrollListener extends EventListener {
    void onScroll(int offset);
  }
  @SuppressWarnings("unused")
  void addScrollListener(ScrollListener listener);

  @SuppressWarnings("unused")
  void removeScrollListener(ScrollListener listener);
}
