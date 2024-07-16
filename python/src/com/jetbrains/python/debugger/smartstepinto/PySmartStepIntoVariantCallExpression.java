// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.smartstepinto;

import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PySmartStepIntoVariantCallExpression extends PySmartStepIntoVariant {
  private final @Nullable PyExpression myCallee;

  public PySmartStepIntoVariantCallExpression(@NotNull PyCallExpression element, int callOrder, @NotNull PySmartStepIntoContext context) {
    super(element, callOrder, context);
    myCallee = element.getCallee();
  }

  @Override
  public @Nullable String getFunctionName() {
    return myCallee != null ? myCallee.getName() : null;
  }

  @Override
  public @Nullable TextRange getHighlightRange() {
    if (myCallee == null) return null;

    String calleeName = myCallee.getName();
    if (calleeName == null) return null;

    TextRange range = myCallee.getTextRange();

    // For example, for the `foo().bar().baz()` call expression the callee will be `foo().bar().baz`.
    // The range must be adjusted in such cases to match only the last part which is `baz`.
    if (calleeName.length() < range.getLength()) {
      int diff = range.getLength() - calleeName.length();
      return new TextRange(range.getStartOffset() + diff, range.getEndOffset());
    }
    return range;
  }

  @Override
  public @Nullable String getText() {
    return myCallee != null ? myCallee.getText() + "()" : null;
  }
}
