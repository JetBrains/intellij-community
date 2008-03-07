package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.xml.XmlTag;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Dmitry Avdeev
*/
public abstract class XmlValueReference implements PsiReference {
  protected XmlTag myTag;
  protected TextRange myRange;

  protected XmlValueReference(XmlTag tag) {
    myTag = tag;
    myRange = ElementManipulators.getValueTextRange(tag);
  }

  public PsiElement getElement() {
    return myTag;
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public String getCanonicalText() {
    return myTag.getText().substring(myRange.getStartOffset(),myRange.getEndOffset());
  }

  protected void replaceContent(final String str) throws IncorrectOperationException {
    final ElementManipulator<XmlTag> manipulator = ElementManipulators.getManipulator(myTag);
    manipulator.handleContentChange(myTag, myRange, str);
    myRange = manipulator.getRangeInElement(myTag);
  }

  public boolean isReferenceTo(PsiElement element) {
    return myTag.getManager().areElementsEquivalent(element, resolve());
  }

  public boolean isSoft() {
    return false;
  }
}
