// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.ast.PyAstIfPartIf;
import com.jetbrains.python.psi.PyIfPart;

/**
 * PyIfPart that represents an 'if' part.
 */
public class PyIfPartIfImpl extends PyConditionalStatementPartImpl implements PyAstIfPartIf, PyIfPart {

  public PyIfPartIfImpl(ASTNode astNode) {
    super(astNode);
  }

}
