// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyIfPartElifImpl;
import org.jetbrains.annotations.NotNull;

public class PySmartExecuteSelectionAction extends AnAction {

  private static String getNLinesAfterCaret(Editor editor, int N) {
    VisualPosition caretPos = editor.getCaretModel().getVisualPosition();

    Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcSurroundingRange(editor, caretPos, caretPos);

    LogicalPosition lineStart = lines.first;
    int start = editor.logicalPositionToOffset(lineStart);
    int end = DocumentUtil.getLineTextRange(editor.getDocument(), caretPos.getLine() + N).getEndOffset();
    return editor.getDocument().getCharsSequence().subSequence(start, end).toString();
  }

  /*
   returns true if PsiElement not an evaluable Python statement
   */
  private static boolean isPartialStatement(PsiElement psiElement) {
    return psiElement instanceof PyElsePart ||
           psiElement instanceof PyIfPartElifImpl ||
           psiElement instanceof PyIfPart ||
           psiElement instanceof PyWhilePart ||
           psiElement instanceof PyExceptPart ||
           psiElement instanceof PyFinallyPart ||
           psiElement instanceof PyStatementPart ||
           psiElement instanceof PyStatementList;
  }

  /*
  closest parent that is evaluable
   */
  private static PsiElement getEvaluableParent(PsiElement psiElement) {
    if (psiElement.getNode().getElementType() == PyTokenTypes.ELSE_KEYWORD ||
        psiElement.getNode().getElementType() == PyTokenTypes.ELIF_KEYWORD ||
        psiElement.getNode().getElementType() == PyTokenTypes.EXCEPT_KEYWORD ||
        psiElement.getNode().getElementType() == PyTokenTypes.FINALLY_KEYWORD) {
      psiElement = psiElement.getParent();
    }
    return isPartialStatement(psiElement) ? psiElement.getParent() : psiElement;
  }

  private static void syntaxErrorAction(final AnActionEvent e) {
    PyExecuteSelectionAction.showConsoleAndExecuteCode(e, "# syntax error");
  }

  private static void smartExecuteCode(final AnActionEvent e, final Editor editor) {
    final Document document = editor.getDocument();
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(e.getProject());
    psiDocumentManager.commitDocument(document);
    final PsiFile psiFile = psiDocumentManager.getPsiFile(document);

    final VisualPosition caretPos = editor.getCaretModel().getVisualPosition();
    final int line = caretPos.getLine();

    final int offset = DocumentUtil.getFirstNonSpaceCharOffset(document, line);
    final PsiElement psiElement = psiFile.findElementAt(offset);
    int numLinesToSubmit = document.getLineCount() - line;
    PsiElement lastCommonParent = null;
    for (int i = 0; line + i < document.getLineCount(); ++i) {
      final int lineStartOffset = DocumentUtil.getFirstNonSpaceCharOffset(document, line + i);
      final PsiElement pe = psiFile.findElementAt(lineStartOffset);
      final PsiElement commonParentRaw = pe == null ? pe.getContainingFile() : PsiTreeUtil.findCommonParent(psiElement, pe);
      final PsiElement commonParent = getEvaluableParent(commonParentRaw);
      if (commonParent.getTextOffset() < offset ||
          commonParent instanceof PyFile) { // at new statement
        numLinesToSubmit = i;
        break;
      }
      lastCommonParent = commonParent;
    }
    if (lastCommonParent == null) {
      if (psiElement instanceof PsiWhiteSpace) { // if we are at a blank line
        PyExecuteSelectionAction.moveCaretDown(editor);
        return;
      }
      syntaxErrorAction(e);
      return;
    }

    String codeToSend =
      numLinesToSubmit == 0 ? "" :
      getNLinesAfterCaret(editor, numLinesToSubmit - 1);
    if (PsiTreeUtil.hasErrorElements(lastCommonParent) ||
        psiElement.getTextOffset() < offset) {
      codeToSend = null;
    }
    codeToSend = codeToSend == null ? null : codeToSend.trim();

    if (codeToSend != null && !codeToSend.isEmpty()) {
      PyExecuteSelectionAction.showConsoleAndExecuteCode(e, codeToSend);
    }
    if (codeToSend != null) {
      for (int i = 0; i < numLinesToSubmit; ++i) {
        PyExecuteSelectionAction.moveCaretDown(editor);
      }
    }
    else {
      syntaxErrorAction(e);
      return;
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor != null) {
      final String selectionText = PyExecuteSelectionAction.getSelectionText(editor);
      if (selectionText != null) {
        PyExecuteSelectionAction.showConsoleAndExecuteCode(e, selectionText);
      }
      else {
        smartExecuteCode(e, editor);
      }
    }
  }
}
