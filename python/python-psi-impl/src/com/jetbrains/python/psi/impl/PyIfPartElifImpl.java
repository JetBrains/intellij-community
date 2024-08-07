// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyIfPartElif;
import com.jetbrains.python.psi.stubs.PyIfPartElifStub;

/**
 * PyIfPart that represents an 'elif' part.
 */
public class PyIfPartElifImpl extends PyConditionalStatementPartImpl<PyIfPartElifStub> implements PyIfPartElif {
  public PyIfPartElifImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyIfPartElifImpl(PyIfPartElifStub stub) {
    super(stub, PyStubElementTypes.IF_PART_ELIF);
  }
}
