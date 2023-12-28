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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a class declaration in source.
 */
@ApiStatus.Experimental
public interface PyAstClass extends PsiNameIdentifierOwner, PyAstCompoundStatement, PyAstDocStringOwner, AstScopeOwner,
                                    PyAstDecoratable, PyAstTypedElement, PyAstQualifiedNameOwner, PyAstStatementListContainer,
                                    PyAstWithAncestors,
                                    PyAstTypeParameterListOwner {
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

  /**
   * Effectively collects assignments inside the class body.
   * <p/>
   * This method does not access AST if underlying PSI is stub based.
   * Note that only <strong>own</strong> attrs are fetched, not parent attrs.
   * If you need parent attributes, consider using {@link #getClassAttributesInherited(TypeEvalContext)}
   *
   * @see #getClassAttributesInherited(TypeEvalContext)
   */
  default List<? extends PyAstTargetExpression> getClassAttributes() {
    List<PyAstTargetExpression> result = new ArrayList<>();
    for (PsiElement psiElement : getStatementList().getChildren()) {
      if (psiElement instanceof PyAstAssignmentStatement assignmentStatement) {
        final PyAstExpression[] targets = assignmentStatement.getTargets();
        for (PyAstExpression target : targets) {
          if (target instanceof PyAstTargetExpression) {
            result.add((PyAstTargetExpression)target);
          }
        }
      }
      else if (psiElement instanceof PyAstTypeDeclarationStatement) {
        final PyAstExpression target = ((PyAstTypeDeclarationStatement)psiElement).getTarget();
        if (target instanceof PyAstTargetExpression) {
          result.add((PyAstTargetExpression)target);
        }
      }
    }
    return result;
  }

  /**
   * Returns the list of names in the class' __slots__ attribute, or null if the class
   * does not define such an attribute.
   *
   * @return the list of names or null.
   */
  @Nullable
  default List<String> getOwnSlots() {
    final PyAstTargetExpression slots = ContainerUtil.find(getClassAttributes(), target -> PyNames.SLOTS.equals(target.getName()));
    if (slots != null) {
      final PyAstExpression value = slots.findAssignedValue();

      return value instanceof PyAstStringLiteralExpression
             ? Collections.singletonList(((PyAstStringLiteralExpression)value).getStringValue())
             : PyUtilCore.strListValue(value);
    }

    return null;
  }

  @Override
  @Nullable
  default String getDocStringValue() {
    return DocStringUtilCore.getDocStringValue(this);
  }

  @Override
  @Nullable
  default PyAstTypeParameterList getTypeParameterList() {
    //noinspection unchecked
    return this.<PyAstTypeParameterList>getStubOrPsiChild(PyElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  @Nullable
  default PyAstDecoratorList getDecoratorList() {
    //noinspection unchecked
    return this.<PyAstDecoratorList>getStubOrPsiChild(PyElementTypes.DECORATOR_LIST);
  }

  @Override
  @Nullable
  default PyAstStringLiteralExpression getDocStringExpression() {
    return DocStringUtilCore.findDocStringExpression(getStatementList());
  }
}
