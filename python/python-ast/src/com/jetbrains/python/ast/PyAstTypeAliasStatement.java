// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Represents Type Alias Statement added in <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
@ApiStatus.Experimental
public interface PyAstTypeAliasStatement extends PyAstStatement, PsiNameIdentifierOwner, PyAstTypeParameterListOwner, PyAstTypedElement,
                                                 PyAstQualifiedNameOwner, AstScopeOwner {

  @Override
  @Nullable
  default PsiElement getNameIdentifier() {
    ASTNode nameNode = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    return nameNode != null ? nameNode.getPsi() : null;
  }

  @Override
  @Nullable
  PyAstTypeParameterList getTypeParameterList();

  @Nullable
  default PyAstExpression getTypeExpression() {
    PsiElement child = getLastChild();
    while (child != null && !(child instanceof PyAstExpression)) {
      if (child instanceof PsiErrorElement) return null;
      child = child.getPrevSibling();
    }
    return (PyAstExpression)child;
  }
}
