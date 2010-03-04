package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public interface PyImportElement extends PyElement, NameDefiner, StubBasedPsiElement<PyImportElementStub> {
  @Nullable
  PyReferenceExpression getImportReference();

  @Nullable
  List<String> getImportedQName();

  @Nullable
  PyTargetExpression getAsName();

  /**
   * @return name under which the element is visible, that is, "as name" is there is one, or just name.
   */
  @Nullable
  String getVisibleName();

}
