package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class MavenPsiReference implements PsiReference {
  protected final PsiElement myElement;
  protected final String myText;
  protected final TextRange myRange;

  public MavenPsiReference(PsiElement element,
                           String text,
                           TextRange range) {
    myElement = element;
    myText = text;
    myRange = range;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public String getCanonicalText() {
    return myText;
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public boolean isReferenceTo(PsiElement element) {
    return getElement().getManager().areElementsEquivalent(element, resolve());
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