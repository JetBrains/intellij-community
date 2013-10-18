package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyAnnotation extends PyElement, StubBasedPsiElement<PyAnnotationStub> {
  PyExpression getValue();

  @Nullable
  PyClass resolveToClass();
}
