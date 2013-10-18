package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyImportElement extends PyElement, PyImportedNameDefiner, StubBasedPsiElement<PyImportElementStub> {
  @Nullable
  PyReferenceExpression getImportReferenceExpression();

  @Nullable
  QualifiedName getImportedQName();

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

  /**
   * Resolves the import element to the element being imported.
   *
   * @return the resolve result or null if the resolution failed.
   */
  @Nullable
  PsiElement resolve();
}
