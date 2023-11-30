// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public final class MarkdownEditorWithPreview extends TextEditorWithPreview {
  public static final Key<MarkdownEditorWithPreview> PARENT_SPLIT_EDITOR_KEY = Key.create("parentSplit");
  private boolean myAutoScrollPreview;

  public MarkdownEditorWithPreview(@NotNull TextEditor editor, @NotNull MarkdownPreviewFileEditor preview) {
    super(
      editor,
      preview,
      MarkdownBundle.message("markdown.editor.name"),
      Layout.SHOW_EDITOR_AND_PREVIEW,
      !MarkdownSettings.getInstance(ProjectUtil.currentOrDefaultProject(editor.getEditor().getProject())).isVerticalSplit()
    );
    editor.putUserData(PARENT_SPLIT_EDITOR_KEY, this);
    preview.putUserData(PARENT_SPLIT_EDITOR_KEY, this);

    preview.setMainEditor(editor.getEditor());

    final var project = ProjectUtil.currentOrDefaultProject(editor.getEditor().getProject());
    final var settings = MarkdownSettings.getInstance(project);
    myAutoScrollPreview = settings.isAutoScrollEnabled();

    final var settingsChangedListener = new MarkdownSettings.ChangeListener() {
      private boolean wasVerticalSplitBefore = settings.isVerticalSplit();

      @Override
      public void beforeSettingsChanged(@NotNull MarkdownSettings settings) {
        wasVerticalSplitBefore = settings.isVerticalSplit();
      }

      @Override
      public void settingsChanged(@NotNull MarkdownSettings settings) {
        setAutoScrollPreview(settings.isAutoScrollEnabled());
        if (wasVerticalSplitBefore != settings.isVerticalSplit()) {
          handleLayoutChange(!settings.isVerticalSplit());
        }
      }
    };
    project.getMessageBus().connect(this).subscribe(MarkdownSettings.ChangeListener.TOPIC, settingsChangedListener);
    getTextEditor().getEditor().getScrollingModel().addVisibleAreaListener(new MyVisibleAreaListener(), this);
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
    return myAutoScrollPreview;
  }

  public void setAutoScrollPreview(boolean autoScrollPreview) {
    myAutoScrollPreview = autoScrollPreview;
  }

  @Override
  public void setLayout(@NotNull Layout layout) {
    super.setLayout(layout);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    if (state instanceof MarkdownEditorWithPreviewState actualState) {
      super.setState(actualState.getUnderlyingState());
      setVerticalSplit(actualState.isVerticalSplit());
    }
  }

  @Override
  public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
    final var underlyingState = super.getState(level);
    return new MarkdownEditorWithPreviewState(underlyingState, isVerticalSplit());
  }

  @Override
  protected @NotNull ToggleAction getShowEditorAction() {
    return (ToggleAction)Objects.requireNonNull(ActionUtil.getAction("Markdown.Layout.EditorOnly"));
  }

  @Override
  protected @NotNull ToggleAction getShowEditorAndPreviewAction() {
    return (ToggleAction)Objects.requireNonNull(ActionUtil.getAction("Markdown.Layout.EditorAndPreview"));
  }

  @Override
  protected @NotNull ToggleAction getShowPreviewAction() {
    return (ToggleAction)Objects.requireNonNull(ActionUtil.getAction("Markdown.Layout.PreviewOnly"));
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
