package com.intellij.xml.refactoring;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlInlineHandler implements InlineHandler {
  @Override
  public Settings prepareInlineElement(@NotNull PsiElement element, Editor editor, boolean invokedOnReference) {
    return null;
  }

  @Override
  public void removeDefinition(@NotNull PsiElement element, @NotNull Settings settings) {
  }

  @Override
  public Inliner createInliner(@NotNull PsiElement element, @NotNull Settings settings) {
    return null;
  }
}
