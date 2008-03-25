/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.stubs.StubElement;

public interface PsiClassReferenceListStub extends StubElement<PsiReferenceList> {
  enum Role {
    THROWS_LIST,
    EXTENDS_OR_IMPLEMENTS_LIST
  }

  String[] getReferencedNames();
  Role getRole();
}