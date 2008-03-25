/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiAnnotationStubImpl extends StubBase<PsiAnnotation> implements PsiAnnotationStub {
  private final String myText;

  public PsiAnnotationStubImpl(final StubElement parent, final IStubElementType elementType, final String text) {
    super(parent, elementType);
    myText = text;
  }

  public String getText() {
    return myText;
  }
}