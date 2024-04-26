package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Copy from com.siyeh.ig.psiutils
 * Classes: PsiElementUtil and EquivalenceChecker
 */
public final class PsiElementUtil {
  /**
   * @param method              the method to compare to.
   * @param containingClassName the name of the class which contiains the
   *                            method.
   * @param returnType          the return type, specify null if any type matches
   * @param methodName          the name the method should have
   * @param parameterTypes      the type of the parameters of the method, specify
   *                            null if any number and type of parameters match or an empty array
   *                            to match zero parameters.
   * @return true, if the specified method matches the specified constraints,
   * false otherwise
   */
  public static boolean methodMatches(
    @NotNull PsiMethod method,
    @NonNls @Nullable String containingClassName,
    @Nullable PsiType returnType,
    @NonNls @Nullable String methodName,
    @Nullable List<PsiType> parameterTypes) {
    final String name = method.getName();
    if (methodName != null && !methodName.equals(name)) {
      return false;
    }
    if (parameterTypes != null) {
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != parameterTypes.size()) {
        return false;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        final PsiType type = parameters[i].getType();
        final PsiType parameterType = parameterTypes.get(i);
        if (PsiTypes.nullType().equals(parameterType)) {
          continue;
        }
        if (parameterType != null && !typesAreEquivalent(type, parameterType)) {
          return false;
        }
      }
    }
    if (returnType != null) {
      final PsiType methodReturnType = method.getReturnType();
      if (!typesAreEquivalent(returnType, methodReturnType)) {
        return false;
      }
    }
    if (containingClassName != null) {
      final PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, containingClassName);
    }
    return true;
  }

  public static boolean typesAreEquivalent(@Nullable PsiType type1, @Nullable PsiType type2) {
    if (type1 == null) {
      return type2 == null;
    }
    if (type2 == null) {
      return false;
    }
    final String type1Text = type1.getCanonicalText();
    final String type2Text = type2.getCanonicalText();
    return type1Text.equals(type2Text);
  }
}
