// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstDictCompExpression;

/**
 * Dict comprehension: {x:x+1 for x in range(10)}
 */
public interface PyDictCompExpression extends PyAstDictCompExpression, PyComprehensionElement {
}
