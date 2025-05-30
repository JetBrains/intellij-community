// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiReference;
import com.intellij.sh.codeInsight.ShPsiReferenceSupport;
import com.intellij.sh.psi.*;
import org.jetbrains.annotations.NotNull;

public final class ShPsiImplUtil {
  static PsiReference @NotNull [] getReferences(@NotNull ShLiteral o) {
    ShPsiReferenceSupport service = ApplicationManager.getApplication().getService(ShPsiReferenceSupport.class);
    return service != null ? service.getReferences(o) : PsiReference.EMPTY_ARRAY;
  }

  static PsiReference @NotNull [] getReferences(@NotNull ShLiteralExpression o) {
    ShPsiReferenceSupport service = ApplicationManager.getApplication().getService(ShPsiReferenceSupport.class);
    return service != null ? service.getReferences(o) : PsiReference.EMPTY_ARRAY;
  }

  static PsiReference @NotNull [] getReferences(@NotNull ShVariable o) {
    ShPsiReferenceSupport service = ApplicationManager.getApplication().getService(ShPsiReferenceSupport.class);
    return service != null ? service.getReferences(o) : PsiReference.EMPTY_ARRAY;
  }
}
