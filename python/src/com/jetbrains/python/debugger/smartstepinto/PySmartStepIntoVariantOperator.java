// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.smartstepinto;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyPrefixExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PySmartStepIntoVariantOperator extends PySmartStepIntoVariant {
  @NonNls private static final Map<String, String> UNARY_OPERATOR_MAPPING = ImmutableMap.of(
    "__sub__", "__neg__",
    "__add__", "__pos__",
    "__invert__", "__invert__"
  );

  public PySmartStepIntoVariantOperator(@NotNull PsiElement element,
                                        int callOrder,
                                        @NotNull PySmartStepIntoContext context) {
    super(element, callOrder, context);
  }

  @Nullable
  @Override
  public String getFunctionName() {
    if (myElement instanceof LeafPsiElement) {
      return ((PyElementType)((LeafPsiElement)myElement).getElementType()).getSpecialMethodName();
    }
    else if (myElement instanceof PyPrefixExpression) {
      return getUnaryOperatorSpecialMethodName(((PyPrefixExpression)myElement).getOperator());
    }
    return null;
  }

  @NotNull
  @Override
  public String getText() {
    String text =  myElement.getText();
    return myElement instanceof PyPrefixExpression ? text.substring(0, 1) : text;
  }

  @NotNull
  @Override
  public TextRange getHighlightRange() {
    TextRange textRange = myElement.getTextRange();
    return myElement instanceof PyPrefixExpression ?
           new TextRange(textRange.getStartOffset(), textRange.getStartOffset() + 1) : textRange;
  }

  @Nullable
  public static String getUnaryOperatorSpecialMethodName(@NotNull PyElementType operator) {
    return UNARY_OPERATOR_MAPPING.getOrDefault(operator.getSpecialMethodName(), null);
  }
}
