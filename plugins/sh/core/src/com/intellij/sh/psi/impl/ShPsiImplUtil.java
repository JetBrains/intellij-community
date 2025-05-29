// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.sh.codeInsight.ShFunctionReferenceSupport;
import com.intellij.sh.codeInsight.ShIncludeCommandReferenceSupport;
import com.intellij.sh.psi.*;
import org.jetbrains.annotations.NotNull;

public final class ShPsiImplUtil {
  static PsiReference @NotNull [] getReferences(@NotNull ShLiteral o) {
    if (o instanceof ShString || o.getWord() != null) {
      PsiReference[] array = ReferenceProvidersRegistry.getReferencesFromProviders(o);
      int length = array.length;
      PsiReference[] result = new PsiReference[length + 2];
      System.arraycopy(array, 0, result, 2, length);
      result[0] = ApplicationManager.getApplication().getService(ShIncludeCommandReferenceSupport.class).resolveReference(o);
      result[1] = ApplicationManager.getApplication().getService(ShFunctionReferenceSupport.class).resolveReference(o);
      return result;
    }
    return PsiReference.EMPTY_ARRAY;
  }

  static PsiReference @NotNull [] getReferences(@NotNull ShLiteralExpression o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }

  static PsiReference @NotNull [] getReferences(@NotNull ShVariable o) {
    return PsiReference.EMPTY_ARRAY;
  }
}
