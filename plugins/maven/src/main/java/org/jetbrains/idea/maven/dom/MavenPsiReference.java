package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class MavenPsiReference implements PsiReference {
  final protected PsiElement myElement;
  final protected String myResolvedText;
  final protected String myOriginalText;
  final protected TextRange myRange;

  public MavenPsiReference(PsiElement element,
                           String originalText,
                           String resolvedText,
                           TextRange range) {
    myElement = element;
    myOriginalText = originalText;
    myResolvedText = resolvedText;
    myRange = range;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public String getCanonicalText() {
    return myOriginalText;
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isSoft() {
    return true;
  }
}