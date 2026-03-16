// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.impl.references.PyKeywordReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyForPartImpl extends PyElementImpl implements PyForPart {
  public PyForPartImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PsiReference getReference() {
    var inNode = getNode().findChildByType(PyTokenTypes.IN_KEYWORD);
    if (inNode == null) return null;

    TextRange range = inNode.getPsi().getTextRangeInParent();
    PyResolveContext resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeAnalysis(getProject(), getContainingFile()));
    return new PyKeywordReference(this, resolveContext, range);
  }
}
