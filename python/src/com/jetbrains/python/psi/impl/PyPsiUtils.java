/*
 * @author max
 */
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementType;

public class PyPsiUtils {
  protected static <T extends PyElement> T[] nodesToPsi(ASTNode[] nodes, T[] array) {
    T[] psiElements = (T[])java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), nodes.length);
    for (int i = 0; i < nodes.length; i++) {
      //noinspection unchecked
      psiElements[i] = (T)nodes[i].getPsi();
    }
    return psiElements;
  }

  protected static ASTNode getPrevComma(ASTNode after) {
    ASTNode node = after;
    PyElementType comma = PyTokenTypes.COMMA;
    do {
      node = node.getTreePrev();
    }
    while (node != null && !node.getElementType().equals(comma));
    return node;
  }

  protected static ASTNode getNextComma(ASTNode after) {
    ASTNode node = after;
    PyElementType comma = PyTokenTypes.COMMA;
    do {
      node = node.getTreeNext();
    }
    while (node != null && !node.getElementType().equals(comma));
    return node;
  }
}