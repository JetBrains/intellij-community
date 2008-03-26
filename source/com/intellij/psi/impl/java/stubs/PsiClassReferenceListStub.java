/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.stubs.StubElement;

public interface PsiClassReferenceListStub extends StubElement<PsiReferenceList> {
  String[] getReferencedNames();
  PsiReferenceList.Role getRole();
}