// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.ApiStatus;

/**
 * Used for literal None and literal ... (Ellipsis).
 */
@ApiStatus.Experimental
public interface PyAstNoneLiteralExpression extends PyAstLiteralExpression {
  default boolean isEllipsis() {
    return getNode().findChildByType(PyTokenTypes.DOT) != null;
  }
}
