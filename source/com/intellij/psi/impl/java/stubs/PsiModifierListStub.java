/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiModifierList;
import com.intellij.psi.stubs.StubElement;

public interface PsiModifierListStub extends StubElement<PsiModifierList> {
  int getModifiersMask();
}