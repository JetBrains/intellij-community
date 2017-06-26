/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public static boolean isTerminationStreamCall(@NotNull PsiMethodCallExpression expression) {
    return checkStreamCall(expression, true, false);
  }

  public static boolean isProducerStreamCall(@NotNull PsiMethodCallExpression expression) {
    return checkStreamCall(expression, false, true);
  }

  private static boolean isIntermediateStreamCall(@NotNull PsiMethodCallExpression expression) {
    return checkStreamCall(expression, true, true);
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
