// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.javafx;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.intellij.plugins.markdown.ui.preview.MarkdownAccessor;
import org.jetbrains.annotations.NotNull;

final class ImageRefreshFix {
  static {
    MarkdownAccessor.setImageRefreshFixAccessor(new MarkdownAccessor.ImageRefreshFixAccessor() {
      @Override
      public String setStamps(@NotNull String html) {
        return ImageRefreshFix.setStamps(html);
      }
    });
  }

  private ImageRefreshFix() {
  }

  @NotNull
  static String setStamps(@NotNull String html) {
    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    final String pattern = "<img src=\"file:";

    StringBuilder sb = new StringBuilder();
    int processedOffset = 0;
    while (true) {
      final int nextI = html.indexOf(pattern, processedOffset);
      if (nextI == -1) {
        break;
      }

      final int nextJ = html.indexOf('"', nextI + pattern.length());
      if (nextJ == -1) {
        return html;
      }

      sb.append(html, processedOffset, nextI + pattern.length());

      final String url = html.substring(nextI + pattern.length(), nextJ);
      sb.append(processUrl(virtualFileManager, url));
      sb.append('"');
      processedOffset = nextJ + 1;
    }

    if (processedOffset < html.length()) {
      sb.append(html, processedOffset, html.length());
    }
    return sb.toString();
  }

  @NotNull
  private static String processUrl(@NotNull VirtualFileManager virtualFileManager, @NotNull String url) {
    final VirtualFile virtualFile = virtualFileManager.findFileByUrl("file://" + url);
    if (virtualFile == null) {
      return url;
    }
    return url + "?stamp=" + virtualFile.getModificationStamp();
  }
}
