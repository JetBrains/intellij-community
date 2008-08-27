package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
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
public class PyComprehensionElementImpl extends PyElementImpl implements NameDefiner {
  public PyComprehensionElementImpl(ASTNode astNode) {
    super(astNode);
  }

  /**
   * In "[x+1 for x in (1,2,3)]" result expression is "x+1".
   * @return result expression.
   */
  @Nullable
  public PyExpression getResultExpression() {
    ASTNode[] exprs = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    return exprs.length == 0 ? null : (PyExpression)exprs[0].getPsi();
  }

  /**
   * In "[x+1 for x in (1,2,3)]" a "for component" is "x".
   * @return all "for components"
   */
  public List<ComprhForComponent> getForComponents() {
    ASTNode node = getNode().getFirstChildNode();
    List<ComprhForComponent> list = new ArrayList<ComprhForComponent>(5);
    while (node != null) {
      IElementType type = node.getElementType();
      ASTNode next = getNextExpression(node);
      if (next == null) break;
      if (type == PyTokenTypes.FOR_KEYWORD) {
        ASTNode next2 = getNextExpression(next);
        if (next2 == null) break;
        final PyExpression variable = (PyExpression)next.getPsi();
        final PyExpression iterated = (PyExpression)next2.getPsi();
        list.add(new ComprhForComponent() {
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

  public List<ComprhIfComponent> getIfComponents() {
    ASTNode node = getNode().getFirstChildNode();
    List<ComprhIfComponent> list = new ArrayList<ComprhIfComponent>(5);
    while (node != null) {
      IElementType type = node.getElementType();
      ASTNode next = getNextExpression(node);
      if (next == null) break;
      if (type == PyTokenTypes.IF_KEYWORD) {
        final PyExpression test = (PyExpression)next.getPsi();
        list.add(new ComprhIfComponent() {
          public PyExpression getTest() {
            return test;
          }
        });
      }
      node = node.getTreeNext();
    }
    return list;
  }

  @Nullable
  private static ASTNode getNextExpression(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreeNext();
    }
    while (node != null && !PyElementTypes.EXPRESSIONS.contains(node.getElementType()));
    return node;
  }

  /**
   * In "[x+1 for x in (1,2,3) if x > 2]" an "if component" is "x > 2".
   * @return all "if components"
   */
  @NotNull
  public Iterable<PyElement> iterateNames() {
    // extract whatever names are defined in "for" components
    List<ComprhForComponent> fors = getForComponents();
    PyElement[] for_targets = new PyElement[fors.size()];
    int i = 0;
    for (ComprhForComponent for_comp : fors) {
      for_targets[i] = for_comp.getIteratorVariable();
      i += 1;
    }
    List<PyElement> name_refs = PyUtil.flattenedParens(for_targets);
    return name_refs;
  }

  public PsiElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false;
  }
}
