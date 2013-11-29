package org.jetbrains.postfixCompletion.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CommonUtils {
  private CommonUtils() {
  }

  public static boolean isNiceExpression(@NotNull PsiElement expression) {
    if (expression instanceof PsiAssignmentExpression) return false;
    if (expression instanceof PsiPrefixExpression) return false;
    if (expression instanceof PsiPostfixExpression) return false;

    if (expression instanceof PsiMethodCallExpression) {
      PsiType expressionType = ((PsiMethodCallExpression)expression).getType();
      if (expressionType != null && expressionType.equals(PsiType.VOID)) return false;
    }

    return true;
  }

  @NotNull
  public static CtorAccessibility isTypeCanBeInstantiatedWithNew(
    @Nullable PsiClass psiClass, @NotNull PsiElement accessContext) {
    if (psiClass == null) return CtorAccessibility.NotAccessible;

    if (psiClass.isEnum()) return CtorAccessibility.NotAccessible;
    if (psiClass.isInterface()) return CtorAccessibility.WithDefaultCtor;

    PsiClass containingType = PsiTreeUtil.getParentOfType(accessContext, PsiClass.class);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(accessContext.getProject());
    PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();

    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) return CtorAccessibility.WithDefaultCtor;

    boolean hasAccessibleCtors = false, hasParametricCtors = false;

    for (PsiMethod constructor : constructors) {
      if (resolveHelper.isAccessible(constructor, accessContext, containingType)) {
        hasAccessibleCtors = true;
        int parametersCount = constructor.getParameterList().getParametersCount();
        if (parametersCount != 0) hasParametricCtors = true;
      }
    }

    if (!hasAccessibleCtors) return CtorAccessibility.NotAccessible;

    return hasParametricCtors ? CtorAccessibility.WithParametricCtor
      : CtorAccessibility.WithDefaultCtor;
  }

  public enum CtorAccessibility {
    NotAccessible,
    WithDefaultCtor,
    WithParametricCtor
  }

  public static boolean isTypeRequiresRefinement(@Nullable PsiClass psiClass) {
    if (psiClass == null) return false;

    if (psiClass.isInterface()) return true;
    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true;

    return false;
  }
}

