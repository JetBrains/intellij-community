/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiModifierListStubImpl extends StubBase<PsiModifierList> implements PsiModifierListStub {
  private final int myMask;

  public PsiModifierListStubImpl(final StubElement parent, final IStubElementType elementType, final int mask) {
    super(parent, elementType);
    myMask = mask;
  }

  public int getModifiersMask() {
    return myMask;
  }
}