/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.java.stubs.PsiParameterListStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiParameterListStubImpl extends StubBase<PsiParameterList> implements PsiParameterListStub {
  public PsiParameterListStubImpl(final StubElement parent, final IStubElementType elementType) {
    super(parent, elementType);
  }
}