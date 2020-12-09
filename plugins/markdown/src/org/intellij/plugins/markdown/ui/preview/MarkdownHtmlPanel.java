// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EventListener;

public interface MarkdownHtmlPanel extends Disposable {
  @NotNull JComponent getComponent();

  void setHtml(@NotNull String html, int initialScrollOffset);

  void reloadWithOffset(int offset);

  void scrollToMarkdownSrcOffset(int offset);

  interface ScrollListener extends EventListener {
    void onScroll(int offset);
  }

  @SuppressWarnings("unused")
  void addScrollListener(ScrollListener listener);

  @SuppressWarnings("unused")
  void removeScrollListener(ScrollListener listener);
}
