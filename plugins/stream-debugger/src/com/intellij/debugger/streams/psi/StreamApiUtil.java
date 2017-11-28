// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamApiUtil {
  private StreamApiUtil() {
  }

  public static boolean isStreamCall(@NotNull PsiMethodCallExpression expression) {
    return isIntermediateStreamCall(expression) || isProducerStreamCall(expression) || isTerminationStreamCall(expression);
  }

  public static boolean isProducerStreamCall(@NotNull PsiMethodCallExpression expression) {
    final PsiMethod method = expression.resolveMethod();

    return (method != null && method.hasModifierProperty(PsiModifier.STATIC)) ||
           checkStreamCall(expression, false, true);
  }

  private static boolean isIntermediateStreamCall(@NotNull PsiMethodCallExpression expression) {
    return checkStreamCall(expression, true, true);
  }

  public static boolean isTerminationStreamCall(@NotNull PsiMethodCallExpression expression) {
    return checkStreamCall(expression, true, false);
  }

  private static boolean checkStreamCall(@NotNull PsiMethodCallExpression expression,
                                         boolean mustParentBeStream,
                                         boolean mustResultBeStream) {
    final PsiMethod method = expression.resolveMethod();
    if (method != null && mustResultBeStream == isStreamType(expression.getType())) {
      final PsiElement methodClass = method.getParent();
      if (methodClass instanceof PsiClass) {
        return mustParentBeStream == isStreamType((PsiClass)methodClass);
      }
    }

    return false;
  }

  @Contract("null -> false")
  private static boolean isStreamType(@Nullable PsiType psiType) {
    return InheritanceUtil.isInheritor(psiType, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM);
  }

  @Contract("null -> false")
  private static boolean isStreamType(@Nullable PsiClass psiClass) {
    return InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM);
  }
}
