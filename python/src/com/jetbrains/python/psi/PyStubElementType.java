/*
 * @author max
 */
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PyStubElementType extends PyElementType {
  private static final Class[] PARAMETER_TYPES = new Class[] {PsiElement.class, StubElement.class, IElementType.class};

  public PyStubElementType(@NonNls final String debugName, final Class<? extends PsiElement> psiElementClass) {
    super(debugName, psiElementClass);
  }

  public @NotNull PsiElement createElement(PsiElement parent, StubElement stubElement) {
    assert _psiElementClass != null;

    try {
      return _psiElementClass.getConstructor(PARAMETER_TYPES).newInstance(parent, stubElement, this);
    }
    catch (Exception e) {
      throw new IllegalStateException(e);
    }

  }
}