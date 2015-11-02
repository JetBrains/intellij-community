/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
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
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PyExpression && PyUtil.isInstanceAttribute((PyExpression)element)) {
      PyClass elementClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
      PyClass referenceClass = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
      if (referenceClass != null && referenceClass.isSubclass(elementClass, null)) {
        String elementName = ((PyTargetExpression) element).getReferencedName();
        String referenceName = myElement.getStringValue();
        if (Comparing.equal(elementName, referenceName)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
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
