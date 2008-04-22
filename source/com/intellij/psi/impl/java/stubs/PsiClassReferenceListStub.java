/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.stubs.StubElement;

public interface PsiClassReferenceListStub extends StubElement<PsiReferenceList> {
  PsiClassType[] getReferencedTypes();
  String[] getReferencedNames();
  PsiReferenceList.Role getRole();
}