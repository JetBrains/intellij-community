// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PsiReferenceEx;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;


@ApiStatus.Internal
public class PyDunderSlotsReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx {
  public PyDunderSlotsReference(@NotNull PyStringLiteralExpression element) {
    super(element, element.getStringValueTextRanges().get(0));
  }

  @Override
  public PsiElement resolve() {
    PyClass referenceClass = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
    return referenceClass == null ? null : referenceClass.findInstanceAttribute(myElement.getStringValue(), true);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (element instanceof PyTargetExpression targetExpression && PyUtil.isInstanceAttribute(targetExpression)) {
      PyClass elementClass = PsiTreeUtil.getParentOfType(targetExpression, PyClass.class);
      PyClass referenceClass = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
      if (referenceClass != null && referenceClass.isSubclass(elementClass, null)) {
        String elementName = targetExpression.getReferencedName();
        String referenceName = myElement.getStringValue();
        if (Objects.equals(elementName, referenceName)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    return null;
  }

  @Override
  public String getUnresolvedDescription() {
    return null;
  }
}
