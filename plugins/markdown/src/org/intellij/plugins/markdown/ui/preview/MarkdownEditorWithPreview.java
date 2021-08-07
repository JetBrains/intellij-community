// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.util.Key;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class MarkdownEditorWithPreview extends TextEditorWithPreview {
  public static final Key<MarkdownEditorWithPreview> PARENT_SPLIT_EDITOR_KEY = Key.create("parentSplit");
  private boolean myAutoScrollPreview = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isAutoScrollPreview();

  public MarkdownEditorWithPreview(@NotNull TextEditor editor, @NotNull MarkdownPreviewFileEditor preview) {
    super(
      editor,
      preview,
      MarkdownBundle.message("markdown.editor.name"),
      Layout.SHOW_EDITOR_AND_PREVIEW,
      !MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit()
    );

    editor.putUserData(PARENT_SPLIT_EDITOR_KEY, this);
    preview.putUserData(PARENT_SPLIT_EDITOR_KEY, this);

    preview.setMainEditor(editor.getEditor());

    MarkdownApplicationSettings.SettingsChangedListener settingsChangedListener =
      new MarkdownApplicationSettings.SettingsChangedListener() {
        @Override
        public void settingsChanged(@NotNull MarkdownApplicationSettings settings) {
          setAutoScrollPreview(settings.getMarkdownPreviewSettings().isAutoScrollPreview());
          handleLayoutChange(!settings.getMarkdownPreviewSettings().isVerticalSplit());
        }
      };

    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(MarkdownApplicationSettings.SettingsChangedListener.TOPIC, settingsChangedListener);

    getTextEditor().getEditor().getScrollingModel().addVisibleAreaListener(new MyVisibleAreaListener());
  }

  public boolean isAutoScrollPreview() {
    return myAutoScrollPreview;
  }

  public void setAutoScrollPreview(boolean autoScrollPreview) {
    myAutoScrollPreview = autoScrollPreview;
  }

  @Override
  protected @Nullable ActionGroup createLeftToolbarActionGroup() {
    return null;
  }

  private class MyVisibleAreaListener implements VisibleAreaListener {
    private int previousLine = 0;

    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent event) {
      if (!isAutoScrollPreview()) {
        return;
      }
      final Editor editor = event.getEditor();
      int y = editor.getScrollingModel().getVerticalScrollOffset();
      int currentLine = editor instanceof EditorImpl ? editor.yToVisualLine(y) : y / editor.getLineHeight();
      if (currentLine == previousLine) {
        return;
      }
      previousLine = currentLine;
      ((MarkdownPreviewFileEditor)getPreviewEditor()).scrollToSrcOffset(EditorUtil.getVisualLineEndOffset(editor, currentLine));
    }
  }
}
