// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PyExecuteConsoleCustomizer;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyExecuteSelectionAction extends DumbAwareAction {

  public PyExecuteSelectionAction() {
    super(PyBundle.messagePointer("python.execute.selection.action.execute.selection.in.console"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor instanceof EditorImpl) {
      PyExecuteConsoleCustomizer.Companion.getInstance().notifySciCellGutterExecuted((EditorImpl)editor, "ExecuteInPyConsoleAction");
    }
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (editor != null && project != null) {
      PythonRunConfiguration config = PyExecuteConsoleCustomizer.Companion.getInstance().getContextConfig(e.getDataContext());
      final String selectionText = getSelectionText(editor);
      if (!PyExecuteInConsole.checkIfAvailableAndShowHint(editor)) return;
      if (selectionText != null) {
        PyExecuteInConsole.executeCodeInConsole(project, selectionText, editor, true, true, false, config);
      }
      else {
        TextRange range = EditorUtil.calcCaretLineTextRange(editor);
        if (!range.isEmpty()) {
          String line = editor.getDocument().getText(range);
          PyExecuteInConsole.executeCodeInConsole(project, line.trim(), editor, true, true, false, config);
          editor.getCaretModel().moveToOffset(range.getEndOffset());
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }
    }
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
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
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
        enabled = !text.isEmpty();
      }
      else if (!EditorUtil.calcCaretLineTextRange(editor).isEmpty()) {
        presentation.setText(PyBundle.message("python.execute.selection.action.execute.line.in.console"));
        enabled = true;
      }
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
