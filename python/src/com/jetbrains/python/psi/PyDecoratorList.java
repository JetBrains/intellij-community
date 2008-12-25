package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import com.jetbrains.python.psi.types.PyDecorator;
import org.jetbrains.annotations.NotNull;

/**
 * A list of function decorators.
 * User: dcheryasov
 * Date: Sep 28, 2008
 */
public interface PyDecoratorList extends PyElement, StubBasedPsiElement<PyDecoratorListStub> {

  /**
   * @return decorators of function, in order of declaration (outermost first).
   */
  @NotNull
  PyDecorator[] getDecorators();
}
