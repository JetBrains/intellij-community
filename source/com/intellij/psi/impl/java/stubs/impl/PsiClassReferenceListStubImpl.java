/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiClassReferenceListStubImpl extends StubBase<PsiReferenceList> implements PsiClassReferenceListStub {
  private final PsiReferenceList.Role myRole;
  private final String[] myNames;

  public PsiClassReferenceListStubImpl(final StubElement parent, final String[] names, final PsiReferenceList.Role role) {
    super(parent, JavaStubElementTypes.REFLIST);
    myNames = names;
    myRole = role;
  }

  public String[] getReferencedNames() {
    return myNames;
  }

  public PsiReferenceList.Role getRole() {
    return myRole;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiRefListStub[").append(myRole.name()).append(":");
    for (int i = 0; i < myNames.length; i++) {
      if (i > 0) builder.append(", ");
      builder.append(myNames[i]);
    }
    builder.append("]");
    return builder.toString();
  }
}