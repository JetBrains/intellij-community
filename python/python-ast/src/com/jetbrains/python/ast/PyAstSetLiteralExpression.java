// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;

/**
 * Represents a Python 3 set literal expression, for example, <tt>{1, 2, 3}</tt>
 */
@ApiStatus.Experimental
public interface PyAstSetLiteralExpression extends PyAstSequenceExpression {
}
