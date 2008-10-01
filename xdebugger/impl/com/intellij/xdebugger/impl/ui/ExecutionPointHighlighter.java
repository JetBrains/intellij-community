package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ExecutionPointHighlighter {
  private final Project myProject;
  private RangeHighlighter myRangeHighlighter;
  private Editor myEditor;
  private XSourcePosition mySourcePosition;
  private OpenFileDescriptor myOpenFileDescriptor;
  private boolean myUseSelection;

  public ExecutionPointHighlighter(final Project project) {
    myProject = project;
  }

  public void show(final @NotNull XSourcePosition position, final boolean useSelection) {
    DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
      public void run() {
        doShow(position, useSelection);
      }
    });
  }

  public void hide() {
    DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
      public void run() {
        doHide();
      }
    });
  }

  public void navigateTo() {
    if (myOpenFileDescriptor != null) {
      FileEditorManager.getInstance(myProject).openTextEditor(myOpenFileDescriptor, false);
    }
  }

  @Nullable
  public VirtualFile getCurrentFile() {
    return myOpenFileDescriptor != null ? myOpenFileDescriptor.getFile() : null;
  }

  public void update() {
    show(mySourcePosition, myUseSelection);
  }

  private void doShow(@NotNull XSourcePosition position, final boolean useSelection) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeHighlighter();

    mySourcePosition = position;
    myEditor = openEditor();
    myUseSelection = useSelection;
    if (myEditor != null) {
      addHighlighter();
    }
  }

  @Nullable
  private Editor openEditor() {
    VirtualFile file = mySourcePosition.getFile();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    int offset = mySourcePosition.getOffset();
    if (offset < 0 || offset >= document.getTextLength()) {
      myOpenFileDescriptor = new OpenFileDescriptor(myProject, file, mySourcePosition.getLine(), 0);
    }
    else {
      myOpenFileDescriptor = new OpenFileDescriptor(myProject, file, offset);
    }
    return FileEditorManager.getInstance(myProject).openTextEditor(myOpenFileDescriptor, false);
  }

  private void doHide() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeHighlighter();
    myOpenFileDescriptor = null;
    myEditor = null;
  }

  private void removeHighlighter() {
    if (myUseSelection && myEditor != null) {
      myEditor.getSelectionModel().removeSelection();
    }
    if (myRangeHighlighter == null || myEditor == null) return;

    myEditor.getMarkupModel().removeHighlighter(myRangeHighlighter);
    myRangeHighlighter = null;
  }

  private void addHighlighter() {
    int line = mySourcePosition.getLine();
    if (myUseSelection) {
      Document document = myEditor.getDocument();
      myEditor.getSelectionModel().setSelection(document.getLineStartOffset(line), document.getLineEndOffset(line) + document.getLineSeparatorLength(line));
      return;
    }

    if (myRangeHighlighter != null) return;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myRangeHighlighter = myEditor.getMarkupModel().addLineHighlighter(line, DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                                                                      scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES));
  }
}
