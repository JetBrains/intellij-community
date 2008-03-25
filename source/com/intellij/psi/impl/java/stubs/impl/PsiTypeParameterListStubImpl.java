/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiTypeParameterListStubImpl extends StubBase<PsiTypeParameterList> implements PsiTypeParameterListStub{
  public PsiTypeParameterListStubImpl(final StubElement parent, final IStubElementType elementType) {
    super(parent, elementType);
  }
}