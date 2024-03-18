// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.PyComprehensionComponent;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyComprehensionIfComponent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@ApiStatus.Experimental
public interface PyAstComprehensionElement extends PyAstExpression, PyAstNamedElementContainer {
  /**
   * In "[x+1 for x in (1,2,3)]" result expression is "x+1".
   *
   * @return result expression.
   */
  @Nullable
  default PyAstExpression getResultExpression() {
    ASTNode[] exprs = getNode().getChildren(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    return exprs.length == 0 ? null : (PyAstExpression)exprs[0].getPsi();
  }

  default List<PyComprehensionComponent> getComponents() {
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

  /**
   * In "[x+1 for x in (1,2,3)]" a "for component" is "x".
   * @return all "for components"
   */
  default List<PyComprehensionForComponent> getForComponents() {
    final List<PyComprehensionForComponent> list = new ArrayList<>(5);
    visitComponents(new ComprehensionElementVisitor() {
      @Override
      void visitForComponent(PyComprehensionForComponent component) {
        list.add(component);
      }
    });
    return list;
  }

  default List<PyComprehensionIfComponent> getIfComponents() {
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
  @NotNull
  default List<PsiNamedElement> getNamedElements() {
    // extract whatever names are defined in "for" components
    List<? extends PyComprehensionForComponent> fors = getForComponents();
    PyAstExpression[] for_targets = new PyAstExpression[fors.size()];
    int i = 0;
    for (PyComprehensionForComponent for_comp : fors) {
      for_targets[i] = for_comp.getIteratorVariable();
      i += 1;
    }
    final List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndLists(for_targets);
    final List<PsiNamedElement> results = new ArrayList<>();
    for (PyAstExpression expression : expressions) {
      if (expression instanceof PsiNamedElement) {
        results.add((PsiNamedElement)expression);
      }
    }
    return results;
  }

  @Nullable
  private static ASTNode getNextExpression(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreeNext();
    }
    while (node != null && !PythonDialectsTokenSetProvider.getInstance().getExpressionTokens().contains(node.getElementType()));
    return node;
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
        final PyAstExpression variable = (PyAstExpression)next.getPsi();
        final PyAstExpression iterated = (PyAstExpression)next2.getPsi();
        final boolean isAsync = Optional
          .ofNullable(node.getTreePrev())
          .map(ASTNode::getTreePrev)
          .map(asyncNode -> asyncNode.getElementType() == PyTokenTypes.ASYNC_KEYWORD)
          .orElse(false);

        visitor.visitForComponent(new PyComprehensionForComponent() {
          @Override
          public PyAstExpression getIteratorVariable() {
            return variable;
          }

          @Override
          public PyAstExpression getIteratedList() {
            return iterated;
          }

          @Override
          public boolean isAsync() {
            return isAsync;
          }
        });
      }
      else if (type == PyTokenTypes.IF_KEYWORD) {
        final PyAstExpression test = (PyAstExpression)next.getPsi();
        visitor.visitIfComponent(new PyComprehensionIfComponent() {
          @Override
          public PyAstExpression getTest() {
            return test;
          }
        });
      }
      node = node.getTreeNext();
    }
  }

  abstract class ComprehensionElementVisitor {
    void visitIfComponent(PyComprehensionIfComponent component) {
    }

    void visitForComponent(PyComprehensionForComponent component) {
    }
  }
}
