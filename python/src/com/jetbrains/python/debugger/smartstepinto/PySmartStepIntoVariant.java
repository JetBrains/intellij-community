// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.smartstepinto;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An abstract class for a thing we can smart step into.
 * <p>
 * Should you need to support another Python expression as a smart step into target,
 * subclass this class and add the respective method override to {@code PySmartStepIntoVariantVisitor}.
 *
 */
public abstract class PySmartStepIntoVariant extends XSmartStepIntoVariant {
  @NotNull protected final PsiElement myElement;
  protected final int myCallOrder;
  @NotNull protected final PySmartStepIntoContext myContext;

  protected PySmartStepIntoVariant(@NotNull PsiElement element, int callOrder, @NotNull PySmartStepIntoContext context) {
    myElement = element;
    myCallOrder = callOrder;
    myContext = context;
  }

  @Nullable public abstract String getFunctionName();

  public int getCallOrder() {
    return myCallOrder;
  }

  @NotNull public PySmartStepIntoContext getContext() {
    return myContext;
  }

  @Override
  @Nullable public TextRange getHighlightRange() {
    return myElement.getTextRange();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PySmartStepIntoVariant variant)) return false;
    return myElement.equals(variant.myElement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myElement);
  }
}
