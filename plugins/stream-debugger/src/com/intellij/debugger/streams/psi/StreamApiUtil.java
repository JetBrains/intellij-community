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

import com.intellij.debugger.streams.lib.LibraryManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.js.descriptorUtils.DescriptorUtilsKt;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.types.KotlinType;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamApiUtil {
  private StreamApiUtil() {
  }

  public static boolean isStreamCall(@NotNull PsiMethodCallExpression expression) {
    return isIntermediateStreamCall(expression) || isProducerStreamCall(expression) || isTerminationStreamCall(expression);
  }

  public static boolean isStreamCall(@NotNull KtCallExpression expression) {
    return isIntermediateStreamCall(expression) || isProducerStreamCall(expression) || isTerminationStreamCall(expression);
  }

  public static boolean isProducerStreamCall(@NotNull KtCallExpression expression) {
    return checkCallSupported(expression, false, true);
  }

  private static boolean isIntermediateStreamCall(@NotNull KtCallExpression expression) {
    return checkCallSupported(expression, true, true);
  }

  public static boolean isTerminationStreamCall(@NotNull KtCallExpression expression) {
    return checkCallSupported(expression, true, false);
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

  private static boolean checkCallSupported(@NotNull KtCallExpression expression,
                                            boolean shouldSupportReceiver,
                                            boolean shouldSupportResult) {
    final KotlinType receiverType = KotlinPsiUtilKt.receiverType(expression);
    final KotlinType resultType = KotlinPsiUtilKt.resolveType(expression);

    final LibraryManager manager = LibraryManager.getInstance(expression.getProject());
    return (receiverType == null || // there is no producer or producer is a static method
            shouldSupportReceiver == isSupportedType(receiverType, manager)) &&
           shouldSupportResult == isSupportedType(resultType, manager);
  }

  private static boolean isSupportedType(@Nullable KotlinType type, @NotNull LibraryManager manager) {
    if (type == null) {
      return false;
    }

    final String typeName = DescriptorUtilsKt.getJetTypeFqName(type, false);
    return manager.isPackageSupported(StringUtil.getPackageName(typeName));
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
