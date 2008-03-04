package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.wordSelection.AbstractWordSelectioner;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;

/**
 * @author yole
 */
public class PyWordSelectionHandler extends AbstractWordSelectioner {
  public boolean canSelect(final PsiElement e) {
    return e.getNode().getElementType() == PyTokenTypes.IDENTIFIER;
  }
}
