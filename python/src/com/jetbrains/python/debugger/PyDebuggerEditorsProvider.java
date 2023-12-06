// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XNamedTreeNode;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.InlineDebuggerHelper;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyDebuggerEditorsProvider extends XDebuggerEditorsProvider {
  @Override
  public @NotNull FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Override
  public @NotNull Document createDocument(final @NotNull Project project,
                                          @NotNull XExpression expression,
                                          final @Nullable XSourcePosition sourcePosition,
                                          @NotNull EvaluationMode mode) {
    String text = expression.getExpression().trim();
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "fragment.py", text, true);

    // Bind to context
    final PsiElement element = getContextElement(project, sourcePosition);
    fragment.setContext(element);

    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  @VisibleForTesting
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
              return PyPsiUtils.getStatement(element);
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

  private static final class PyInlineDebuggerHelper extends InlineDebuggerHelper {
    private static final PyInlineDebuggerHelper INSTANCE = new PyInlineDebuggerHelper();

    @Override
    public boolean shouldEvaluateChildrenByDefault(XNamedTreeNode node) {
      return false;
    }
  }

  @Override
  public @NotNull InlineDebuggerHelper getInlineDebuggerHelper() {
    return PyInlineDebuggerHelper.INSTANCE;
  }
}
