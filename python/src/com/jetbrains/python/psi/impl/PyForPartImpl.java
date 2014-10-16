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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;

import java.util.List;

/**
 * User: dcheryasov
 * Date: Mar 15, 2009 9:57:48 PM
 */
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

  public PyExpression getTarget() {
    ASTNode n = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    if (followsNodeOfType(n, PyTokenTypes.FOR_KEYWORD)) {
      return (PyExpression)n.getPsi(); // can't be null, 'if' would fail
    }
    else return null;
  }

  public PyExpression getSource() {
    List<PsiElement> exprs = findChildrenByType(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    // normally there are 2 exprs, the second is the source.
    if (exprs.size() != 2) return null; // could be a parsing error
    PyExpression ret = (PyExpression)exprs.get(1);
    if (followsNodeOfType(ret.getNode(), PyTokenTypes.IN_KEYWORD)) return ret;
    else return null;
  }

}
