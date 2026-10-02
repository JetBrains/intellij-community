// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.jetbrains.python.psi.PyListLiteralExpression;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public final class PyConvertLiteralToListIntention extends PyBaseConvertCollectionLiteralIntention {
  public PyConvertLiteralToListIntention() {
    super(PyListLiteralExpression.class, "list", "[", "]");
  }
}
