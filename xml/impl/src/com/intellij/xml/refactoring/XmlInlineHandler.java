package com.intellij.xml.refactoring;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlInlineHandler implements InlineHandler {
  @Override
  public Settings prepareInlineElement(PsiElement element, Editor editor, boolean invokedOnReference) {
    return null;
  }

  @Override
  public void removeDefinition(PsiElement element, Settings settings) {
  }

  @Override
  public Inliner createInliner(PsiElement element, Settings settings) {
    return null;
  }
}
