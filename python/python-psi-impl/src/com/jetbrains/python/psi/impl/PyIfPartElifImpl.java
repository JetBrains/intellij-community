// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyIfPartElif;

/**
 * PyIfPart that represents an 'elif' part.
 */
public class PyIfPartElifImpl extends PyConditionalStatementPartImpl implements PyIfPartElif {
  public PyIfPartElifImpl(ASTNode astNode) {
    super(astNode);
  }
}
