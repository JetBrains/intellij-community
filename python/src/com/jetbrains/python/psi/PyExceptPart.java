package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author dcheryasov
 */
public interface PyExceptPart extends PyElement, StubBasedPsiElement<PyExceptPartStub>, NameDefiner, PyStatementPart {
  PyExceptPart[] EMPTY_ARRAY = new PyExceptPart[0];

  @Nullable
  PyExpression getExceptClass();

  @Nullable
  PyExpression getTarget();
}
