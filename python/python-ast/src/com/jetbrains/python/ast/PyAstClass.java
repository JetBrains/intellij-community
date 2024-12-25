/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a class declaration in source.
 */
@ApiStatus.Experimental
public interface PyAstClass extends PsiNameIdentifierOwner, PyAstCompoundStatement, PyAstDocStringOwner, AstScopeOwner,
                                    PyAstDecoratable, PyAstTypedElement, PyAstQualifiedNameOwner, PyAstStatementListContainer,
                                    PyAstWithAncestors, PyAstTypeParameterListOwner {
  PyAstClass[] EMPTY_ARRAY = new PyAstClass[0];
  ArrayFactory<PyAstClass> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PyAstClass[count];

  @Override
  default @Nullable String getName() {
    ASTNode node = getNameNode();
    return node != null ? node.getText() : null;
  }

  @Override
  default @Nullable PsiElement getNameIdentifier() {
    final ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

  default @Nullable ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  default @NotNull PyAstStatementList getStatementList() {
    final PyAstStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for class " + getText();
    return statementList;
  }

  /**
   * Returns a PSI element for the super classes list.
   * <p/>
   * Operates at the AST level.
   */
  default @Nullable PyAstArgumentList getSuperClassExpressionList() {
    final PyAstArgumentList argList = PsiTreeUtil.getChildOfType(this, PyAstArgumentList.class);
    if (argList != null && argList.getFirstChild() != null) {
      return argList;
    }
    return null;
  }

  /**
   * Returns PSI elements for the expressions in the super classes list.
   * <p/>
   * Operates at the AST level.
   */
  PyAstExpression @NotNull [] getSuperClassExpressions();

  @Override
  default @Nullable String getDocStringValue() {
    return DocStringUtilCore.getDocStringValue(this);
  }

  @Override
  default @Nullable PyAstStringLiteralExpression getDocStringExpression() {
    return DocStringUtilCore.findDocStringExpression(getStatementList());
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyClass(this);
  }
}
