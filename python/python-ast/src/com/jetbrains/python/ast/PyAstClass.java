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
  @Nullable
  default String getName() {
    ASTNode node = getNameNode();
    return node != null ? node.getText() : null;
  }

  @Nullable
  @Override
  default PsiElement getNameIdentifier() {
    final ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

  @Nullable
  default ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  @NotNull
  default PyAstStatementList getStatementList() {
    final PyAstStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for class " + getText();
    return statementList;
  }

  /**
   * Returns a PSI element for the super classes list.
   * <p/>
   * Operates at the AST level.
   */
  @Nullable
  default PyAstArgumentList getSuperClassExpressionList() {
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
  @Nullable
  default String getDocStringValue() {
    return DocStringUtilCore.getDocStringValue(this);
  }

  @Override
  @Nullable
  default PyAstStringLiteralExpression getDocStringExpression() {
    return DocStringUtilCore.findDocStringExpression(getStatementList());
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyClass(this);
  }
}
