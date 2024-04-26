// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.smartstepinto;

import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyGeneratorExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PySmartStepIntoVariantComprehension extends PySmartStepIntoVariant {

  protected PySmartStepIntoVariantComprehension(@NotNull PyComprehensionElement element, int callOrder,
                                                @NotNull PySmartStepIntoContext context) {
    super(element, callOrder, context);
  }

  @Override
  public @NotNull String getFunctionName() {
    return getText();
  }

  @Override
  public @NonNls @NotNull String getText() {
    if (myElement instanceof PyGeneratorExpression)
      return "<genexpr>";
    else if (myElement instanceof PyListCompExpression)
      return "<listcomp>";
    else if (myElement instanceof PyDictCompExpression)
      return "<dictcomp>";
    else
      return "<setcomp>";
  }

  public static boolean isComprehensionName(@NotNull String name) {
    return PyStackFrame.COMPREHENSION_NAMES.contains(name);
  }
}
