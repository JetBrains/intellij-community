// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.EventListener;

public interface MarkdownHtmlPanel extends ScrollableMarkdownPreview, Disposable {
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
    setHtml(html, initialScrollOffset, (VirtualFile)null);
  }

  /**
   * Updates current HTML content with the new one.
   * @param html new HTML content.
   * @param initialScrollOffset Offset in the original document which will be used to initially position preview content.
   * @param documentPath Path to original document. It will be used to resolve resources with relative paths, like images.
   */
  default void setHtml(@NotNull String html, int initialScrollOffset, @Nullable Path documentPath) {
    if (documentPath == null) {
      setHtml(html, initialScrollOffset, (VirtualFile) null);
    } else {
      setHtml(html, initialScrollOffset, VfsUtil.findFile(documentPath, false));
    }
  }

  void setHtml(@NotNull String html, int initialScrollOffset, @Nullable VirtualFile document);

  default void setHtml(@NotNull String html, int initialScrollOffset, int initialScrollLineNumber, @Nullable VirtualFile document) {
    setHtml(html, initialScrollOffset, document);
  }

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

   /**
   * @deprecated implement {@code scrollTo(editor, line, $completion)} instead
   */
  @Deprecated
  default void scrollToMarkdownSrcOffset(int offset, boolean smooth) {}

  @Override
  @Nullable
  default Object scrollTo(@NotNull Editor editor, int line, @NotNull Continuation<? super @NotNull Unit> $completion) {
    scrollToMarkdownSrcOffset(EditorUtil.getVisualLineEndOffset(editor, line), true);
    return null;
  }

  interface ScrollListener extends EventListener {
    void onScroll(int offset);
  }
  @SuppressWarnings("unused")
  void addScrollListener(ScrollListener listener);

  @SuppressWarnings("unused")
  void removeScrollListener(ScrollListener listener);
}
