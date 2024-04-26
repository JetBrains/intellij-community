// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public abstract class PythonTemplateContextType extends TemplateContextType {

  public PythonTemplateContextType(@NotNull @NlsContexts.Label String presentableName) {
    super(presentableName);
  }

  @Override
  public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
    PsiFile file = templateActionContext.getFile();
    if (isPythonLanguage(file, templateActionContext.getStartOffset())) {
      final PsiElement element = file.findElementAt(templateActionContext.getStartOffset());
      if (element != null) {
        if (!templateActionContext.isSurrounding()) {
          if (isAfterDot(element) || element instanceof PsiComment || isInsideStringLiteral(element) || isInsideParameterList(element)) {
            return false;
          }
        }
        return isInContext(element);
      }
    }
    return false;
  }

  protected abstract boolean isInContext(@NotNull PsiElement element);

  private static boolean isPythonLanguage(@NotNull PsiFile file, int offset) {
    return PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(PythonLanguage.getInstance());
  }

  private static boolean isInsideStringLiteral(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class, false) != null;
  }

  private static boolean isInsideParameterList(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PyParameterList.class) != null;
  }

  private static boolean isAfterDot(@NotNull PsiElement element) {
    final PsiElementPattern.Capture<PsiElement> capture = psiElement().afterLeafSkipping(psiElement().whitespace(),
                                                                                         psiElement().withElementType(PyTokenTypes.DOT));
    return capture.accepts(element, new ProcessingContext());
  }

  public static final class General extends PythonTemplateContextType {

    public General() {
      super("Python"); //NON-NLS
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return true;
    }
  }

  public static final class Class extends PythonTemplateContextType {
    public Class() {
      super(PyBundle.message("live.template.context.class"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, PyClass.class) != null;
    }
  }

  public static final class TopLevel extends PythonTemplateContextType {
    public TopLevel() {
      super(PyBundle.message("live.template.context.top.level"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      ScopeOwner owner = ScopeUtil.getScopeOwner(element);
      if (!(owner instanceof PyFile)) return false;
      PyExpressionStatement statement = PsiTreeUtil.getParentOfType(element, PyExpressionStatement.class);
      return statement != null && statement.getParent() instanceof PyFile;
    }
  }
}
