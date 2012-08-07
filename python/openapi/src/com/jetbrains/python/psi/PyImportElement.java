package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyImportElement extends PyElement, NameDefiner, StubBasedPsiElement<PyImportElementStub> {
  @Nullable
  PyReferenceExpression getImportReferenceExpression();

  @Nullable
  PyQualifiedName getImportedQName();

  @Nullable
  PyTargetExpression getAsNameElement();

  @Nullable
  String getAsName();

  /**
   * @return name under which the element is visible, that is, "as name" is there is one, or just name.
   */
  @Nullable
  String getVisibleName();

  PyStatement getContainingImportStatement();
  
  @Nullable
  PsiElement getElementNamed(String name, boolean resolveImportElement);
}
