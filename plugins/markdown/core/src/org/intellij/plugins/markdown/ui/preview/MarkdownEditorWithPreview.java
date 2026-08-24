// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.Point;

public final class MarkdownEditorWithPreview extends TextEditorWithPreview implements MarkdownHeaderNavigationHandler {
  private final MarkdownSettings settings;
  private boolean autoScrollPreview;

  /**
   * @deprecated use constructor overload with {@link MarkdownSettings} parameter
   */
  @Deprecated
  public MarkdownEditorWithPreview(@NotNull TextEditor editor, @NotNull MarkdownPreviewFileEditor preview, @NotNull Project project) {
    this(editor, preview, project, MarkdownSettings.getInstance(project));
  }

  public MarkdownEditorWithPreview(@NotNull TextEditor editor,
                                   @NotNull MarkdownPreviewFileEditor preview,
                                   @NotNull Project project,
                                   @NotNull MarkdownSettings settings) {
    super(
      editor,
      preview,
      MarkdownBundle.message("markdown.editor.name"),
      Layout.SHOW_EDITOR_AND_PREVIEW,
      !settings.isVerticalSplit()
    );

    this.settings = settings;

    // allow launching actions while in preview mode;
    // FIXME: better solution IDEA-354102
    editor.getEditor().getContentComponent().putClientProperty(ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, true);
    preview.setMainEditor(editor.getEditor());

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
  }

  @Override
  protected @NotNull JBSplitter createSplitter() {
    JBSplitter splitter = super.createSplitter();
    // "Preview layout" must apply to a freshly created editor, not just to an already open one.
    splitter.setOrientation(!settings.isVerticalSplit());
    return splitter;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    super.setState(state);
    // "Preview layout" is a global default, so it must win over the per-file orientation
    // that super.setState() restores from the editor state. See IJPL-253568.
    handleLayoutChange(!settings.isVerticalSplit());
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

  @Override
  public void navigateToHeader(int textOffset, int lineNumber) {
    ((MarkdownPreviewFileEditor)myPreview).scrollToLine(myEditor.getEditor(), lineNumber);
  }

  private final class MyVisibleAreaListener implements VisibleAreaListener {
    private int previousLine = 0;

    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent event) {
      if (!isAutoScrollPreview()) {
        return;
      }

      final Editor editor = event.getEditor();
      int y = editor.getScrollingModel().getVerticalScrollOffset();
      int currentLine = editor instanceof EditorImpl ? editor.xyToLogicalPosition(new Point(0, y)).getLine() : y / editor.getLineHeight();
      if (currentLine == previousLine) {
        return;
      }

      previousLine = currentLine;
      ((MarkdownPreviewFileEditor)myPreview).scrollToLine(editor, currentLine);
    }
  }
}
