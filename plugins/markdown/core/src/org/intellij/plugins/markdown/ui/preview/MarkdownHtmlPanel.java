// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.EventListener;

public interface MarkdownHtmlPanel extends Disposable {
  @NotNull JComponent getComponent();

  /**
   * Updates current HTML content with the new one.
   * <br/>
   * Note: If you want local paths inside the html to be correctly resolved, use {@link #setHtml(String, int, Path)} instead.
   *
   * @param html new HTML content.
   * @param initialScrollOffset Offset in the original document which will be used to initially position preview content.
   */
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

  /**
   * @return null if current preview implementation doesn't support any message passing.
   */
  @ApiStatus.Experimental
  default @Nullable BrowserPipe getBrowserPipe() {
    return null;
  }

  @ApiStatus.Experimental
  default @Nullable Project getProject() {
    return null;
  }

  @ApiStatus.Experimental
  default @Nullable VirtualFile getVirtualFile() {
    return null;
  }

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
