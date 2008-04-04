/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiTypeParameterListStubImpl extends StubBase<PsiTypeParameterList> implements PsiTypeParameterListStub{
  public PsiTypeParameterListStubImpl(final StubElement parent) {
    super(parent, JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiTypeParameterListStub";
  }
}