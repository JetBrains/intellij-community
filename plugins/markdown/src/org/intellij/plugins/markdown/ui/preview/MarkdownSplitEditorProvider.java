package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import org.intellij.plugins.markdown.ui.split.SplitTextEditorProvider;
import org.jetbrains.annotations.NotNull;

public class MarkdownSplitEditorProvider extends SplitTextEditorProvider {
  public MarkdownSplitEditorProvider() {
    super(new PsiAwareTextEditorProvider(), new MarkdownPreviewFileEditorProvider());
  }

  @Override
  protected FileEditor createSplitEditor(@NotNull final FileEditor firstEditor, @NotNull FileEditor secondEditor) {
    if (!(firstEditor instanceof TextEditor) || !(secondEditor instanceof MarkdownPreviewFileEditor)) {
      throw new IllegalArgumentException("Main editor should be TextEditor");
    }
    return new MarkdownSplitEditor(((TextEditor)firstEditor), ((MarkdownPreviewFileEditor)secondEditor));
  }
}
