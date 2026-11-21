// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstCallExpression;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstExpressionStatement;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.ast.PyAstLiteralExpression;
import com.jetbrains.python.ast.PyAstNamedParameter;
import com.jetbrains.python.ast.PyAstQualifiedExpression;
import com.jetbrains.python.ast.PyAstReferenceExpression;
import com.jetbrains.python.ast.PyAstStatementList;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAstElementGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class PyDebugSupportUtils {

  private PyDebugSupportUtils() {
  }

  // can expression be evaluated, or should be executed
  public static boolean isExpression(final Project project, final String expression) {
    return ReadAction.compute(() -> {

      final PsiFile file = PyAstElementGenerator.getInstance(project).createDummyFile(LanguageLevel.getDefault(), expression);
      return file.getFirstChild() instanceof PyAstExpressionStatement && file.getFirstChild() == file.getLastChild();
    });
  }

  public static @Nullable TextRange getExpressionRangeAtOffset(final Project project, final Document document, final int offset) {
    return ReadAction.compute(() -> {

      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        PsiElement element = psiFile.findElementAt(offset);
        if (!(element instanceof PyAstExpression) || element instanceof PyAstLiteralExpression) {
          element = PsiTreeUtil.getParentOfType(element, PyAstExpression.class);
        }
        if (element instanceof PyAstLiteralExpression) {
          return null;
        }
        if (element instanceof PyAstReferenceExpression && element.getParent() instanceof PyAstCallExpression parent) {
          // Don't evaluate function objects, expand range for the entire call (`foo` -> `foo(arg1, ..., argN)`)
          element = parent;
        }
        if (element != null && isSimpleEnough(element) && isExpression(project, document.getText(element.getTextRange()))) {
          return element.getTextRange();
        }
      }
      return null;
    });
  }

  // is expression suitable to quick evaluate/display tooltip
  private static boolean isSimpleEnough(final PsiElement element) {
    return element instanceof PyAstLiteralExpression ||
           element instanceof PyAstQualifiedExpression ||
           element instanceof PyAstNamedParameter ||
           element instanceof PyAstCallExpression;
  }

  // is expression a variable reference and can be evaluated
  // todo: use patterns (?)
  public static boolean canSaveToTemp(final Project project, final String expression) {
    return ReadAction.compute(() -> {

      final PsiFile file = PyAstElementGenerator.getInstance(project).createDummyFile(LanguageLevel.getDefault(), expression);
      final PsiElement root = file.getFirstChild();
      return !isVariable(root) && (root instanceof PyAstExpressionStatement);
    });
  }

  private static Boolean isVariable(PsiElement root) {
    return root instanceof PyAstExpressionStatement &&
           root.getFirstChild() instanceof PyAstReferenceExpression &&
           root.getFirstChild() == root.getLastChild() &&
           root.getFirstChild().getFirstChild() != null &&
           root.getFirstChild().getFirstChild().getNode().getElementType() == PyTokenTypes.IDENTIFIER &&
           root.getFirstChild().getFirstChild() == root.getFirstChild().getLastChild() &&
           root.getFirstChild().getFirstChild().getFirstChild() == null;
  }

  private static @Nullable String getLineText(@NotNull Document document, int line) {
    if (line > 0 && line < document.getLineCount()) {
      return document.getText(TextRange.create(document.getLineStartOffset(line), document.getLineEndOffset(line)));
    }
    return null;
  }

  public static boolean isContinuationLine(@NotNull Document document, int line) {
    String text = getLineText(document, line);
    if (text != null && text.trim().endsWith("\\")) {
      return true;
    }

    return false;
  }

  public static boolean isCurrentPythonDebugProcess(@NotNull AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    return session != null && session.getDebugProcess() instanceof PyDebugProcess;
  }

  public static @Nullable PsiElement getContextElement(final Project project, XSourcePosition sourcePosition) {
    if (sourcePosition != null) {
      final Document document = FileDocumentManager.getInstance().getDocument(sourcePosition.getFile());
      if (document == null) return null;
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        int offset = sourcePosition.getOffset();
        if (offset >= 0 && offset < document.getTextLength()) {
          final int lineEndOffset = document.getLineEndOffset(document.getLineNumber(offset));
          do {
            PsiElement element = psiFile.findElementAt(offset);
            if (element != null && !(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
              return getStatement(element);
            }
            if (element == null) return null;
            offset = element.getTextRange().getEndOffset();
          }
          while (offset < lineEndOffset);
        }
      }
    }
    return null;
  }

  private static @Nullable PsiElement getStatement(@NotNull PsiElement element) {
    PsiElement statementList = element instanceof PyAstFile || element instanceof PyAstStatementList
                               ? element
                               : PsiTreeUtil.getParentOfType(element, PyAstFile.class, PyAstStatementList.class);
    if (statementList == null) return null;
    return PyPsiUtilsCore.getParentRightBefore(element, statementList);
  }
}
