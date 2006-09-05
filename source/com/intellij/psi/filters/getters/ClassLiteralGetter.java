package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

public class ClassLiteralGetter implements ContextGetter {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.filters.getters.ClassLiteralGetter");
  private ContextGetter myBaseGetter;

  public ClassLiteralGetter(ContextGetter baseGetter) {
    myBaseGetter = baseGetter;
  }

  public Object[] get(PsiElement context, CompletionContext completionContext) {
    for (final Object element : myBaseGetter.get(context, completionContext)) {
      if (element instanceof PsiClassType) {
        PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)element).resolveGenerics();
        PsiClass psiClass = resolveResult.getElement();
        if (psiClass != null && "java.lang.Class".equals(psiClass.getQualifiedName())) {
          final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          if (typeParameters.length == 1) {
            PsiType substitution = resolveResult.getSubstitutor().substitute(typeParameters[0]);
            if (substitution instanceof PsiWildcardType) {
              substitution = ((PsiWildcardType)substitution).getBound();
            }
            if (substitution instanceof PsiClassType && !((PsiClassType)substitution).hasParameters()) {
              final @NonNls String suffix = ".class";
              try {
                final PsiManager manager = psiClass.getManager();
                PsiExpression expr =
                  manager.getElementFactory().createExpressionFromText(substitution.getCanonicalText() + suffix, context);
                expr = (PsiExpression)manager.getCodeStyleManager().shortenClassReferences(expr);
                return new Object[]{expr};
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
