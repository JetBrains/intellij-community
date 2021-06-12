// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PyExecuteConsoleCustomizer;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyExecuteSelectionAction extends DumbAwareAction {

  public PyExecuteSelectionAction() {
    super(PyBundle.messagePointer("python.execute.selection.action.execute.selection.in.console"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (editor != null && project != null) {
      PythonRunConfiguration config = PyExecuteConsoleCustomizer.Companion.getInstance().getContextConfig(e.getDataContext());
      final String selectionText = getSelectionText(editor);
      if (selectionText != null) {
        PyExecuteInConsole.executeCodeInConsole(project, selectionText, editor, true, true, false, config);
      }
      else {
        String line = getLineUnderCaret(editor);
        if (line != null) {
          PyExecuteInConsole.executeCodeInConsole(project, line.trim(), editor, true, true, false, config);
          moveCaretDown(editor);
        }
      }
    }
  }

  private static void moveCaretDown(Editor editor) {
    VisualPosition pos = editor.getCaretModel().getVisualPosition();
    Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcSurroundingRange(editor, pos, pos);
    int offset = editor.getCaretModel().getOffset();

    LogicalPosition lineStart = lines.first;
    LogicalPosition nextLineStart = lines.second;

    int start = editor.logicalPositionToOffset(lineStart);
    int end = editor.logicalPositionToOffset(nextLineStart);

    Document document = editor.getDocument();

    if (nextLineStart.line < document.getLineCount()) {

      int newOffset = end + offset - start;

      int nextLineEndOffset = document.getLineEndOffset(nextLineStart.line);
      if (newOffset >= nextLineEndOffset) {
        newOffset = nextLineEndOffset;
      }

      editor.getCaretModel().moveToOffset(newOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  /**
   * Finds existing or creates a new console and then executes provided code there.
   *
   * @param e
   * @param selectionText null means that there is no code to execute, only open a console
   * @deprecated Use unified `PyExecuteInConsole.executeCodeInConsole` instead with appropriate parameters
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static void showConsoleAndExecuteCode(@NotNull final AnActionEvent e, @Nullable final String selectionText) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    Project project = e.getProject();
    if (project == null) return;
    PyExecuteInConsole.executeCodeInConsole(project, selectionText, editor, true, true, selectionText == null, null);
  }

  private static String getLineUnderCaret(Editor editor) {
    VisualPosition caretPos = editor.getCaretModel().getVisualPosition();

    Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcSurroundingRange(editor, caretPos, caretPos);

    LogicalPosition lineStart = lines.first;
    LogicalPosition nextLineStart = lines.second;
    int start = editor.logicalPositionToOffset(lineStart);
    int end = editor.logicalPositionToOffset(nextLineStart);
    if (end <= start) {
      return null;
    }
    return editor.getDocument().getCharsSequence().subSequence(start, end).toString();
  }

  @Nullable
  private static String getSelectionText(@NotNull Editor editor) {
    if (editor.getSelectionModel().hasSelection()) {
      SelectionModel model = editor.getSelectionModel();

      return model.getSelectedText();
    }
    else {
      return null;
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    Presentation presentation = e.getPresentation();

    boolean enabled = false;
    if (isPython(editor)) {
      String text = getSelectionText(editor);
      if (text != null) {
        presentation.setText(PyBundle.message("python.execute.selection.action.execute.selection.in.console"));
      }
      else {
        text = getLineUnderCaret(editor);
        if (text != null) {
          presentation.setText(PyBundle.message("python.execute.selection.action.execute.line.in.console"));
        }
      }

      enabled = !StringUtil.isEmpty(text);
    }

    presentation.setEnabledAndVisible(enabled);
  }

  public static boolean isPython(Editor editor) {
    if (editor == null) {
      return false;
    }

    Project project = editor.getProject();

    if (project == null) {
      return false;
    }

    PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return psi instanceof PyFile;
  }
}
