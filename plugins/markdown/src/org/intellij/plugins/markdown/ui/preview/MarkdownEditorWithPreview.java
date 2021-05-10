// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class MarkdownEditorWithPreview extends TextEditorWithPreview {
  private boolean myAutoScrollPreview = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isAutoScrollPreview();

  public MarkdownEditorWithPreview(@NotNull TextEditor editor,
                                   @NotNull MarkdownPreviewFileEditor preview) {
    super(editor, preview, MarkdownBundle.message("markdown.editor.name"), Layout.SHOW_EDITOR_AND_PREVIEW, !MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit());
    preview.setMainEditor(editor.getEditor());

    MarkdownApplicationSettings.SettingsChangedListener settingsChangedListener =
      new MarkdownApplicationSettings.SettingsChangedListener() {
        @Override
        public void beforeSettingsChanged(@NotNull MarkdownApplicationSettings newSettings) {
          boolean oldAutoScrollPreview = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isAutoScrollPreview();
          boolean oldVerticalSplit = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit();

          ApplicationManager.getApplication().invokeLater(() -> {
            if (oldAutoScrollPreview == myAutoScrollPreview) {
              setAutoScrollPreview(newSettings.getMarkdownPreviewSettings().isAutoScrollPreview());
            }

            if (oldVerticalSplit == isVerticalSplit()) {
              setVerticalSplit(newSettings.getMarkdownPreviewSettings().isVerticalSplit());
            }
          });
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
  protected @Nullable ActionGroup createRightToolbarActionGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction("Markdown.Toolbar.Right");
  }

  private class MyVisibleAreaListener implements VisibleAreaListener {
    private int previousLine = 0;

    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent event) {
      if (!isAutoScrollPreview()) {
        return;
      }
      final Editor editor = event.getEditor();
      int currentLine = EditorUtil.yPositionToLogicalLine(editor, editor.getScrollingModel().getVerticalScrollOffset());
      if (currentLine == previousLine) {
        return;
      }
      previousLine = currentLine;
      ((MarkdownPreviewFileEditor)getPreview()).scrollToSrcOffset(EditorUtil.getVisualLineEndOffset(editor, currentLine));
    }
  }
}
