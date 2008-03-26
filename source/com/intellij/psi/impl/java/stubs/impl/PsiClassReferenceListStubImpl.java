/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiClassReferenceListStubImpl extends StubBase<PsiReferenceList> implements PsiClassReferenceListStub {
  private PsiReferenceList.Role myRole;
  private String[] myNames;

  public PsiClassReferenceListStubImpl(final StubElement parent, final IStubElementType elementType,
                                       final String[] names, final PsiReferenceList.Role role) {
    super(parent, elementType);
    myNames = names;
    myRole = role;
  }

  public String[] getReferencedNames() {
    return myNames;
  }

  public PsiReferenceList.Role getRole() {
    return myRole;
  }
}