// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public final class PyConvertLiteralToSetIntention extends PyBaseConvertCollectionLiteralIntention {
  public PyConvertLiteralToSetIntention() {
    super(PySetLiteralExpression.class, "set", "{", "}");
  }

  @Override
  protected boolean isAvailableForCollection(@NotNull PySequenceExpression literal) {
    return !literal.isEmpty() && LanguageLevel.forElement(literal).isAtLeast(LanguageLevel.PYTHON27) && !isInTargetPosition(literal);
  }

  private static boolean isInTargetPosition(@NotNull PySequenceExpression sequenceLiteral) {
    return ContainerUtil.exists(sequenceLiteral.getElements(), expression -> expression instanceof PyTargetExpression);
  }
}
