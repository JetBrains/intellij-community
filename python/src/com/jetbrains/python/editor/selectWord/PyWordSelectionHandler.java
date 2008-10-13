package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.wordSelection.AbstractWordSelectioner;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyTokenTypes;

/**
 * @author yole
 */
public class PyWordSelectionHandler extends AbstractWordSelectioner {
  public boolean canSelect(final PsiElement e) {
    final ASTNode astNode = e.getNode();
    return astNode != null && astNode.getElementType() == PyTokenTypes.IDENTIFIER;
  }
}
