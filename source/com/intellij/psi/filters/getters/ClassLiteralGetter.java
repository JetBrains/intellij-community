package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ClassLiteralGetter implements ContextGetter {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.filters.getters.ClassLiteralGetter");
  private ContextGetter myBaseGetter;
  @NonNls private static final String DOT_CLASS = ".class";

  public ClassLiteralGetter(ContextGetter baseGetter) {
    myBaseGetter = baseGetter;
  }

  public Object[] get(PsiElement context, CompletionContext completionContext) {
    for (final Object element : myBaseGetter.get(context, completionContext)) {
      if (element instanceof PsiClassType) {
        PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)element).resolveGenerics();
        PsiClass psiClass = resolveResult.getElement();
        if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
          final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          if (typeParameters.length == 1) {
            PsiType substitution = resolveResult.getSubstitutor().substitute(typeParameters[0]);
            boolean addInheritors = false;
            if (substitution instanceof PsiWildcardType) {
              final PsiWildcardType wildcardType = (PsiWildcardType)substitution;
              substitution = wildcardType.getBound();
              addInheritors = wildcardType.isExtends();
            }

            final List<LookupElement<PsiExpression>> result = new ArrayList<LookupElement<PsiExpression>>();
            createLookupElement(substitution, context, 2, result);
            if (addInheritors && substitution != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(substitution.getCanonicalText())) {
              for (final PsiType type : CodeInsightUtil.addSubtypes(substitution, context, true)) {
                createLookupElement(type, context, 1, result);
              }
            }
            return result.toArray(new Object[result.size()]);

          }
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private static void createLookupElement(@Nullable final PsiType type, final PsiElement context, final int priority, final List<LookupElement<PsiExpression>> list) {
    if (type instanceof PsiClassType && !((PsiClassType)type).hasParameters() && !(((PsiClassType) type).resolve() instanceof PsiTypeParameter)) {
      try {
        final PsiManager manager = context.getManager();
        PsiExpression expr =
          manager.getElementFactory().createExpressionFromText(type.getCanonicalText() + DOT_CLASS, context);
        expr = (PsiExpression)manager.getCodeStyleManager().shortenClassReferences(expr);
        list.add(LookupElementFactory.getInstance().createLookupElement(expr, expr.getText()).setPriority(priority));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
