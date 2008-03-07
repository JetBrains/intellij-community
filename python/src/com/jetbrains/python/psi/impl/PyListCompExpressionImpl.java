/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PsiCached;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.types.PyType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.05.2005
 * Time: 23:33:16
 * To change this template use File | Settings | File Templates.
 */
public class PyListCompExpressionImpl extends PyElementImpl implements PyListCompExpression {
  public PyListCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyListCompExpression(this);
  }

  @PsiCached
  public PyExpression getResultExpression() {
    ASTNode[] exprs = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    return exprs.length == 0 ? null : (PyExpression)exprs[0].getPsi();
  }

  @PsiCached
  public List<ListCompComponent> getComponents() {
    ASTNode node = getNode().getFirstChildNode();
    List<ListCompComponent> list = new ArrayList<ListCompComponent>(5);
    while (node != null) {
      IElementType type = node.getElementType();
      ASTNode next = getNextExpression(node);
      if (next == null) break;
      if (type == PyTokenTypes.IF_KEYWORD) {
        final PyExpression test = (PyExpression)next.getPsi();
        list.add(new IfComponent() {
          public PyExpression getTest() {
            return test;
          }
        });
      }
      else if (type == PyTokenTypes.FOR_KEYWORD) {
        ASTNode next2 = getNextExpression(next);
        if (next2 == null) break;
        final PyExpression variable = (PyExpression)next.getPsi();
        final PyExpression iterated = (PyExpression)next2.getPsi();
        list.add(new ForComponent() {
          public PyExpression getIteratorVariable() {
            return variable;
          }

          public PyExpression getIteratedList() {
            return iterated;
          }
        });
      }
      node = node.getTreeNext();
    }
    return list;
  }

  private static ASTNode getNextExpression(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreeNext();
    }
    while (node != null && !PyElementTypes.EXPRESSIONS.contains(node.getElementType()));
    return node;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for (ListCompComponent component : getComponents()) {
      if (component instanceof ForComponent) {
        //TODO: this needs to restrict resolution based on nesting
        // for example, this is not valid (the i in the first for should not resolve):
        //    x  for x in i for i in y
        ForComponent forComponent = (ForComponent)component;
        if (!forComponent.getIteratorVariable().processDeclarations(processor, substitutor, null, place)) return false;
      }
    }
    return true;
  }

  public PyType getType() {
    return null;
  }
}
