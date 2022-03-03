// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.intellij.plugins.markdown.ui.split.SplitFileEditor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link MarkdownEditorWithPreview}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
public class MarkdownSplitEditor extends SplitFileEditor<TextEditor, MarkdownPreviewFileEditor> implements TextEditor {
  private boolean myAutoScrollPreview = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isAutoScrollPreview();
  private boolean myVerticalSplit = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit();

  public MarkdownSplitEditor(@NotNull TextEditor mainEditor, @NotNull MarkdownPreviewFileEditor secondEditor) {
    super(mainEditor, secondEditor);
    secondEditor.setMainEditor(mainEditor.getEditor());

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

            if (oldVerticalSplit == myVerticalSplit) {
              setVerticalSplit(newSettings.getMarkdownPreviewSettings().isVerticalSplit());
            }
          });
        }
      };

    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(MarkdownApplicationSettings.SettingsChangedListener.TOPIC, settingsChangedListener);

    mainEditor.getEditor().getScrollingModel().addVisibleAreaListener(new MyVisibleAreaListener());
  }

  @NotNull
  @Override
  public String getName() {
    return MarkdownBundle.message("markdown.editor.name");
  }

  @Nullable
  @Override
  public VirtualFile getFile() {
    return getMainEditor().getFile();
  }

  @NotNull
  @Override
  public Editor getEditor() {
    return getMainEditor().getEditor();
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    return getMainEditor().canNavigateTo(navigatable);
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
    getMainEditor().navigateTo(navigatable);
  }

  public boolean isAutoScrollPreview() {
    return myAutoScrollPreview;
  }

  public void setVerticalSplit(boolean verticalSplit) {
    myVerticalSplit = verticalSplit;
  }

  public void setAutoScrollPreview(boolean autoScrollPreview) {
    myAutoScrollPreview = autoScrollPreview;
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
      getSecondEditor().scrollToSrcOffset(EditorUtil.getVisualLineEndOffset(editor, currentLine));
    }
  }
}
