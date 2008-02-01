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

  public ExecutionPointHighlighter(final Project project) {
    myProject = project;
  }

  public void show(final XSourcePosition position) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        doShow(position);
      }
    });
  }

  public void hide() {
    DebuggerUIUtil.invokeLater(new Runnable() {
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

  private void doShow(XSourcePosition position) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeHighlighter();

    myLine = position.getLine();
    myEditor = openEditor(position);
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
    if (myRangeHighlighter == null) return;

    myEditor.getMarkupModel().removeHighlighter(myRangeHighlighter);
    myRangeHighlighter = null;
  }

  private void addHighlighter() {
    if (myRangeHighlighter != null) return;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myRangeHighlighter = myEditor.getMarkupModel().addLineHighlighter(myLine, DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                                                                      scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES));
  }
}
