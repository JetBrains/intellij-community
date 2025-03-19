// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XNamedTreeNode;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.InlineDebuggerHelper;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.impl.PyCodeFragmentWithHiddenImports;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


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
    final PyCodeFragmentWithHiddenImports fragment = new PyCodeFragmentWithHiddenImports(project, "fragment.py", text, true);

    // Bind to context
    final PsiElement element = getContextElement(project, sourcePosition);
    fragment.setContext(element);

    if (expression.getCustomInfo() != null) {
      fragment.addImportsFromStrings(expression.getCustomInfo().lines().toList());
    }
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  @Override
  public @NotNull XExpression createExpression(@NotNull Project project,
                                               @NotNull Document document,
                                               @Nullable Language language,
                                               @NotNull EvaluationMode mode) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file instanceof PyCodeFragmentWithHiddenImports fragment) {
      List<PyImportStatementBase> pseudoImports = fragment.getPseudoImports();
      String customInfo = StringUtil.nullize(StringUtil.join(pseudoImports, PsiElement::getText, "\n"));
      return new XExpressionImpl(fragment.getText(), language, customInfo, mode);
    }
    return super.createExpression(project, document, language, mode);
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
