// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class PyDebugSupportUtils {

  private PyDebugSupportUtils() {
  }

  // can expression be evaluated, or should be executed
  public static boolean isExpression(final Project project, final String expression) {
    return ReadAction.compute(() -> {

      final PsiFile file = PyElementGenerator.getInstance(project).createDummyFile(LanguageLevel.getDefault(), expression);
      return file.getFirstChild() instanceof PyExpressionStatement && file.getFirstChild() == file.getLastChild();
    });
  }

  public static @Nullable TextRange getExpressionRangeAtOffset(final Project project, final Document document, final int offset) {
    return ReadAction.compute(() -> {

      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        PsiElement element = psiFile.findElementAt(offset);
        if (!(element instanceof PyExpression) || element instanceof PyLiteralExpression) {
          element = PsiTreeUtil.getParentOfType(element, PyExpression.class);
        }
        if (element instanceof PyLiteralExpression) {
          return null;
        }
        if (element instanceof PyReferenceExpression && element.getParent() instanceof PyCallExpression parent) {
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
    return element instanceof PyLiteralExpression ||
           element instanceof PyQualifiedExpression ||
           element instanceof PySliceExpression ||
           element instanceof PyNamedParameter ||
           element instanceof PyCallExpression;
  }

  // is expression a variable reference and can be evaluated
  // todo: use patterns (?)
  public static boolean canSaveToTemp(final Project project, final String expression) {
    return ReadAction.compute(() -> {

      final PsiFile file = PyElementGenerator.getInstance(project).createDummyFile(LanguageLevel.getDefault(), expression);
      final PsiElement root = file.getFirstChild();
      return !isVariable(root) && (root instanceof PyExpressionStatement);
    });
  }

  private static Boolean isVariable(PsiElement root) {
    return root instanceof PyExpressionStatement &&
           root.getFirstChild() instanceof PyReferenceExpression &&
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
}
