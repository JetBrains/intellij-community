/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;

public class PsiAnnotationStubImpl extends StubBase<PsiAnnotation> implements PsiAnnotationStub {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl");

  private final String myText;
  private PatchedSoftReference<CompositeElement> myParsedFromRepository;

  public PsiAnnotationStubImpl(final StubElement parent, final String text) {
    super(parent, JavaStubElementTypes.ANNOTATION);
    myText = text;
  }

  public String getText() {
    return myText;
  }

  public CompositeElement getTreeElement() {
    CompositeElement parsed;

    if (myParsedFromRepository != null) {
      parsed = myParsedFromRepository.get();
      if (parsed != null) return parsed;
    }

    final String text = getText();
    try {
      parsed = (CompositeElement)JavaPsiFacade.getInstance(getProject()).getParserFacade().createAnnotationFromText(text, getPsi()).getNode();
      myParsedFromRepository = new PatchedSoftReference<CompositeElement>(parsed);
      assert parsed != null;
      return parsed;
    }
    catch (IncorrectOperationException e) {
      LOG.error("Bad annotation in repository!");
      return null;
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiAnnotationStub[").
        append(myText).
        append("]");
    return builder.toString();
  }
}