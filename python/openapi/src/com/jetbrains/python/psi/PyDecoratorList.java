package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A list of function decorators.
 * @author dcheryasov
 */
public interface PyDecoratorList extends PyElement, StubBasedPsiElement<PyDecoratorListStub> {
  /**
   * @return decorators of function, in order of declaration (outermost first).
   */
  @NotNull
  PyDecorator[] getDecorators();

  @Nullable
  PyDecorator findDecorator(String name);
}
