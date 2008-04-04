/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassInitializerStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiClassInitializerStubImpl extends StubBase<PsiClassInitializer> implements PsiClassInitializerStub {
  public PsiClassInitializerStubImpl(final StubElement parent) {
    super(parent, JavaStubElementTypes.CLASS_INITIALIZER);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiClassInitializerStub";
  }
}