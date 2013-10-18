package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class RenamePyVariableProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    // extension ordering in python-plugin-common.xml ensures that classes and functions are handled by their own processors
    return element instanceof PyElement && !(element instanceof PyReferenceExpression);
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_VARIABLE;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_VARIABLE = enabled;
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename(PsiElement element, @Nullable Editor editor) {
    if (element instanceof PyLambdaExpression) {
      final PyLambdaExpression lambdaExpression = (PyLambdaExpression)element;
      final ScopeOwner owner = ScopeUtil.getScopeOwner(lambdaExpression);
      if (owner instanceof PyClass) {
        final PyClass cls = (PyClass)owner;
        final Property property = cls.findPropertyByCallable(lambdaExpression);
        if (property != null) {
          final PyTargetExpression site = property.getDefinitionSite();
          if (site != null) {
            return site;
          }
        }
      }
      return null;
    }
    return element;
  }
}
