// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstCompoundStatement;

/**
 * Indicates a non-trivial statement that can contain other statements, e.g. a function definition, an "if" statement, a loop, etc.
 * Directly corresponds to the <a href="https://docs.python.org/reference/compound_stmts.html">"Compound statements"</a> section
 * of The Python Language Reference.
 */
public interface PyCompoundStatement extends PyAstCompoundStatement, PyStatement {
}
