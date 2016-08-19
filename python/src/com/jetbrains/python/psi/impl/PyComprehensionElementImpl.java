/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehension-like element base, for list comps ang generators.
 * User: dcheryasov
 * Date: Jul 31, 2008
 */
public abstract class PyComprehensionElementImpl extends PyElementImpl implements PyComprehensionElement {
  public PyComprehensionElementImpl(ASTNode astNode) {
    super(astNode);
  }

  /**
   * In "[x+1 for x in (1,2,3)]" result expression is "x+1".
   * @return result expression.
   */
  @Nullable
  public PyExpression getResultExpression() {
    ASTNode[] exprs = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    return exprs.length == 0 ? null : (PyExpression)exprs[0].getPsi();
  }

  /**
   * In "[x+1 for x in (1,2,3)]" a "for component" is "x".
   * @return all "for components"
   */
  public List<ComprhForComponent> getForComponents() {
    final List<ComprhForComponent> list = new ArrayList<>(5);
    visitComponents(new ComprehensionElementVisitor() {
      @Override
      void visitForComponent(ComprhForComponent component) {
        list.add(component);
      }
    });
    return list;
  }

  private void visitComponents(ComprehensionElementVisitor visitor) {
    ASTNode node = getNode().getFirstChildNode();
    while (node != null) {
      IElementType type = node.getElementType();
      ASTNode next = getNextExpression(node);
      if (next == null) break;
      if (type == PyTokenTypes.FOR_KEYWORD) {
        ASTNode next2 = getNextExpression(next);
        if (next2 == null) break;
        final PyExpression variable = (PyExpression)next.getPsi();
        final PyExpression iterated = (PyExpression)next2.getPsi();
        visitor.visitForComponent(new ComprhForComponent() {
          public PyExpression getIteratorVariable() {
            return variable;
          }

          public PyExpression getIteratedList() {
            return iterated;
          }
        });
      }
      else if (type == PyTokenTypes.IF_KEYWORD) {
        final PyExpression test = (PyExpression)next.getPsi();
        visitor.visitIfComponent(new ComprhIfComponent() {
          public PyExpression getTest() {
            return test;
          }
        });
      }
      node = node.getTreeNext();
    }
  }

  public List<ComprhIfComponent> getIfComponents() {
    final List<ComprhIfComponent> list = new ArrayList<>(5);
    visitComponents(new ComprehensionElementVisitor() {
      @Override
      void visitIfComponent(ComprhIfComponent component) {
        list.add(component);
      }
    });
    return list;
  }

  public List<ComprehensionComponent> getComponents() {
    final List<ComprehensionComponent> list = new ArrayList<>(5);
    visitComponents(new ComprehensionElementVisitor() {
      @Override
      void visitForComponent(ComprhForComponent component) {
        list.add(component);
      }

      @Override
      void visitIfComponent(ComprhIfComponent component) {
        list.add(component);
      }
    });
    return list;
  }

  @Nullable
  private static ASTNode getNextExpression(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreeNext();
    }
    while (node != null && !PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens().contains(node.getElementType()));
    return node;
  }

  @NotNull
  public List<PsiNamedElement> getNamedElements() {
    // extract whatever names are defined in "for" components
    List<ComprhForComponent> fors = getForComponents();
    PyExpression[] for_targets = new PyExpression[fors.size()];
    int i = 0;
    for (ComprhForComponent for_comp : fors) {
      for_targets[i] = for_comp.getIteratorVariable();
      i += 1;
    }
    final List<PyExpression> expressions = PyUtil.flattenedParensAndLists(for_targets);
    final List<PsiNamedElement> results = Lists.newArrayList();
    for (PyExpression expression : expressions) {
      if (expression instanceof PsiNamedElement) {
        results.add((PsiNamedElement)expression);
      }
    }
    return results;
  }

  @Nullable
  public PsiNamedElement getNamedElement(@NotNull final String the_name) {
    return PyUtil.IterHelper.findName(getNamedElements(), the_name);
  }

  abstract class ComprehensionElementVisitor {
    void visitIfComponent(ComprhIfComponent component) {
    }

    void visitForComponent(ComprhForComponent component) {
    }
  }
}
