// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
    final PsiElement element = PyDebugSupportUtils.getContextElement(project, sourcePosition);
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
    XExpression expression = ReadAction.computeBlocking(() -> {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file instanceof PyCodeFragmentWithHiddenImports fragment) {
        List<PyImportStatementBase> pseudoImports = fragment.getPseudoImports();
        String customInfo = StringUtil.nullize(StringUtil.join(pseudoImports, PsiElement::getText, "\n"));
        return new XExpressionImpl(fragment.getText(), language, customInfo, mode);
      }
      return null;
    });
    return expression != null ? expression : super.createExpression(project, document, language, mode);
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
