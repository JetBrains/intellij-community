// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;

import java.util.List;

public class PyForPartImpl extends PyStatementPartImpl implements PyForPart {
  public PyForPartImpl(ASTNode astNode) {
    super(astNode);
  }

  /**
   * Checks that given node actually follows a node of given type, skipping whitespace.
   * @param node node to check.
   * @param eltType type of a node that must precede the node we're checking.
   * @return true if node is really a next sibling to a node of eltType type.
   */
  protected static boolean followsNodeOfType(ASTNode node, PyElementType eltType) {
    if (node != null) {
      PsiElement checker = node.getPsi();
      checker = checker.getPrevSibling(); // step from the source node
      while (checker != null) {
        ASTNode ch_node = checker.getNode();
        if (ch_node == null) return false;
        else {
          if (ch_node.getElementType() == eltType) {
            return true;
          }
          else if (!(checker instanceof PsiWhiteSpace)) {
            return false;
          }
        }
        checker = checker.getPrevSibling();
      }
    }
    return false;
  }

  @Override
  public PyExpression getTarget() {
    ASTNode n = getNode().findChildByType(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    if (followsNodeOfType(n, PyTokenTypes.FOR_KEYWORD)) {
      return (PyExpression)n.getPsi(); // can't be null, 'if' would fail
    }
    else return null;
  }

  @Override
  public PyExpression getSource() {
    List<PsiElement> exprs = findChildrenByType(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    // normally there are 2 exprs, the second is the source.
    if (exprs.size() != 2) return null; // could be a parsing error
    PyExpression ret = (PyExpression)exprs.get(1);
    if (followsNodeOfType(ret.getNode(), PyTokenTypes.IN_KEYWORD)) return ret;
    else return null;
  }

}
