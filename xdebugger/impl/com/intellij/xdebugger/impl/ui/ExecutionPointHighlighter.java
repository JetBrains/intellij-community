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

/**
 * @author nik
 */
public class ExecutionPointHighlighter {
  private final Project myProject;
  private RangeHighlighter myRangeHighlighter;
  private Editor myEditor;
  private int myLine;
  private OpenFileDescriptor myOpenFileDescriptor;
  private boolean myUseSelection;

  public ExecutionPointHighlighter(final Project project) {
    myProject = project;
  }

  public void show(final XSourcePosition position, final boolean useSelection) {
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

  private void doShow(XSourcePosition position, final boolean useSelection) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeHighlighter();

    myLine = position.getLine();
    myEditor = openEditor(position);
    myUseSelection = useSelection;
    if (myEditor != null) {
      addHighlighter();
    }
  }

  @Nullable
  private Editor openEditor(final XSourcePosition position) {
    VirtualFile file = position.getFile();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (position.getOffset() < 0 || position.getOffset() >= document.getTextLength()) {
      return null;
    }
    
    myOpenFileDescriptor = new OpenFileDescriptor(myProject, file, position.getOffset());
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
    if (myUseSelection) {
      Document document = myEditor.getDocument();
      myEditor.getSelectionModel().setSelection(document.getLineStartOffset(myLine), document.getLineEndOffset(myLine) + document.getLineSeparatorLength(myLine));
      return;
    }

    if (myRangeHighlighter != null) return;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myRangeHighlighter = myEditor.getMarkupModel().addLineHighlighter(myLine, DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                                                                      scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES));
  }
}
