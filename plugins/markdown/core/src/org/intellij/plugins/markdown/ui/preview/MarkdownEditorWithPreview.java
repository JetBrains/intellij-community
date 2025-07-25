// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Konstantin Bulenkov
 */
public final class MarkdownEditorWithPreview extends TextEditorWithPreview {
  private boolean autoScrollPreview;

  public MarkdownEditorWithPreview(@NotNull TextEditor editor, @NotNull MarkdownPreviewFileEditor preview, @NotNull Project project) {
    super(
      editor,
      preview,
      MarkdownBundle.message("markdown.editor.name"),
      Layout.SHOW_EDITOR_AND_PREVIEW,
      !MarkdownSettings.getInstance(project).isVerticalSplit()
    );

    // allow launching actions while in preview mode;
    // FIXME: better solution IDEA-354102
    editor.getEditor().getContentComponent().putClientProperty(ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, true);
    preview.setMainEditor(editor.getEditor());

    MarkdownSettings settings = MarkdownSettings.getInstance(project);
    autoScrollPreview = settings.isAutoScrollEnabled();

    project.getMessageBus().connect(this).subscribe(MarkdownSettings.ChangeListener.TOPIC, new MarkdownSettings.ChangeListener() {
      private boolean wasVerticalSplitBefore = settings.isVerticalSplit();

      @Override
      public void beforeSettingsChanged(@NotNull MarkdownSettings settings1) {
        wasVerticalSplitBefore = settings1.isVerticalSplit();
      }

      @Override
      public void settingsChanged(@NotNull MarkdownSettings settings1) {
        setAutoScrollPreview(settings1.isAutoScrollEnabled());
        if (wasVerticalSplitBefore != settings1.isVerticalSplit()) {
          handleLayoutChange(!settings1.isVerticalSplit());
        }
      }
    });

    editor.getEditor().getScrollingModel().addVisibleAreaListener(new MyVisibleAreaListener(), this);
    editor.getEditor().getCaretModel().addCaretListener(new MyCaretListener(), this);
  }

  @Override
  protected void onLayoutChange(Layout oldValue, Layout newValue) {
    super.onLayoutChange(oldValue, newValue);
    // Editor tab will lose focus after switching to JCEF preview for some reason.
    // So we should explicitly request focus for our editor here.
    if (newValue == Layout.SHOW_PREVIEW) {
      requestFocusForPreview();
    }
  }

  private void requestFocusForPreview() {
    final var preferredComponent = myPreview.getPreferredFocusedComponent();
    if (preferredComponent != null) {
      preferredComponent.requestFocus();
      return;
    }
    myPreview.getComponent().requestFocus();
  }

  public boolean isAutoScrollPreview() {
    return autoScrollPreview;
  }

  public void setAutoScrollPreview(boolean autoScrollPreview) {
    this.autoScrollPreview = autoScrollPreview;
  }

  private final class MyCaretListener implements CaretListener {
    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
      if (isAutoScrollPreview()) {
        suppressNextScrollSyncForEditor(event.getEditor());
        ((MarkdownPreviewFileEditor) myPreview).ensureMarkdownSrcOffsetIsVisible(event.getEditor().getCaretModel().getOffset());
      }
    }
  }

  private static final Map<Editor, Long> scrollSuppressions = new WeakHashMap<>();

  public static void suppressNextScrollSyncForEditor(Editor editor) {
    scrollSuppressions.put(editor, System.currentTimeMillis());
  }

  private static void clearOldScrollSuppressions() {
    long now = System.currentTimeMillis();
    var iterator = scrollSuppressions.entrySet().iterator();

    while (iterator.hasNext()) {
      var entry = iterator.next();

      if (entry.getValue() < now - 3000) {
        iterator.remove();
      }
    }
  }

  private final class MyVisibleAreaListener implements VisibleAreaListener {
    private int previousLine = 0;

    private static int pixelOffsetToLine(Editor editor, int y) {
      return editor instanceof EditorImpl ? editor.xyToLogicalPosition(new Point(0, y)).line : y / editor.getLineHeight();
    }

    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent event) {
      // If the user double-clicks the preview, causing the editor to scroll to a new location,
      //   we want to avoid having the editor scrolling turn around and cause the preview to scroll.
      final Editor editor = event.getEditor();

      clearOldScrollSuppressions();

      if (scrollSuppressions.get(editor) != null || !isAutoScrollPreview()) {
        return;
      }

      int y = editor.getScrollingModel().getVerticalScrollOffset();
      int currentLine = pixelOffsetToLine(editor, y);

      if (currentLine == previousLine) {
        return;
      }

      int scrollLine = currentLine;

      if (currentLine > previousLine) {
        scrollLine = pixelOffsetToLine(editor, y + editor.getScrollingModel().getVisibleArea().height);
      }

      ((MarkdownPreviewFileEditor) myPreview)
        .ensureMarkdownSrcOffsetIsVisible(editor.logicalPositionToOffset(new LogicalPosition(scrollLine, 0)));

      previousLine = currentLine;
    }
  }
}
