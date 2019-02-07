// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.Optional;

/**
 * Comprehension-like element base, for list comps ang generators.
 * User: dcheryasov
 */
public abstract class PyComprehensionElementImpl extends PyElementImpl implements PyComprehensionElement {
  public PyComprehensionElementImpl(ASTNode astNode) {
    super(astNode);
  }

  /**
   * In "[x+1 for x in (1,2,3)]" result expression is "x+1".
   * @return result expression.
   */
  @Override
  @Nullable
  public PyExpression getResultExpression() {
    ASTNode[] exprs = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    return exprs.length == 0 ? null : (PyExpression)exprs[0].getPsi();
  }

  /**
   * In "[x+1 for x in (1,2,3)]" a "for component" is "x".
   * @return all "for components"
   */
  @Override
  public List<PyComprehensionForComponent> getForComponents() {
    final List<PyComprehensionForComponent> list = new ArrayList<>(5);
    visitComponents(new ComprehensionElementVisitor() {
      @Override
      void visitForComponent(PyComprehensionForComponent component) {
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
        final boolean isAsync = Optional
          .ofNullable(node.getTreePrev())
          .map(ASTNode::getTreePrev)
          .map(asyncNode -> asyncNode.getElementType() == PyTokenTypes.ASYNC_KEYWORD)
          .orElse(false);

        visitor.visitForComponent(new PyComprehensionForComponent() {
          @Override
          public PyExpression getIteratorVariable() {
            return variable;
          }

          @Override
          public PyExpression getIteratedList() {
            return iterated;
          }

          @Override
          public boolean isAsync() {
            return isAsync;
          }
        });
      }
      else if (type == PyTokenTypes.IF_KEYWORD) {
        final PyExpression test = (PyExpression)next.getPsi();
        visitor.visitIfComponent(new PyComprehensionIfComponent() {
          @Override
          public PyExpression getTest() {
            return test;
          }
        });
      }
      node = node.getTreeNext();
    }
  }

  @Override
  public List<PyComprehensionIfComponent> getIfComponents() {
    final List<PyComprehensionIfComponent> list = new ArrayList<>(5);
    visitComponents(new ComprehensionElementVisitor() {
      @Override
      void visitIfComponent(PyComprehensionIfComponent component) {
        list.add(component);
      }
    });
    return list;
  }

  @Override
  public List<PyComprehensionComponent> getComponents() {
    final List<PyComprehensionComponent> list = new ArrayList<>(5);
    visitComponents(new ComprehensionElementVisitor() {
      @Override
      void visitForComponent(PyComprehensionForComponent component) {
        list.add(component);
      }

      @Override
      void visitIfComponent(PyComprehensionIfComponent component) {
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

  @Override
  @NotNull
  public List<PsiNamedElement> getNamedElements() {
    // extract whatever names are defined in "for" components
    List<PyComprehensionForComponent> fors = getForComponents();
    PyExpression[] for_targets = new PyExpression[fors.size()];
    int i = 0;
    for (PyComprehensionForComponent for_comp : fors) {
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

  abstract static class ComprehensionElementVisitor {
    void visitIfComponent(PyComprehensionIfComponent component) {
    }

    void visitForComponent(PyComprehensionForComponent component) {
    }
  }
}
