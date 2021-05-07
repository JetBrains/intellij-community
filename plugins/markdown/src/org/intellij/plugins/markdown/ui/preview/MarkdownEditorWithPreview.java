// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class MarkdownEditorWithPreview extends TextEditorWithPreview {
  private boolean myAutoScrollPreview = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isAutoScrollPreview();
  private boolean myVerticalSplit = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit();

  public MarkdownEditorWithPreview(@NotNull TextEditor editor,
                                   @NotNull MarkdownPreviewFileEditor preview) {
    super(editor, preview, MarkdownBundle.message("markdown.editor.name"), Layout.SHOW_EDITOR_AND_PREVIEW, !MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit());
    preview.setMainEditor(editor.getEditor());
  }
}
