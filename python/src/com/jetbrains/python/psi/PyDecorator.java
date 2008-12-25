package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import org.jetbrains.annotations.Nullable;

/**
 * Covers a decorator call, e.g. <tt>@staticmethod</tt>.
 * Decorators happen contextually above the function definition, but are stored inside it for convenience.
 * User: dcheryasov
 * Date: Sep 26, 2008
 */
public interface PyDecorator extends /*PyElement*/ PyCallExpression, StubBasedPsiElement<PyDecoratorStub> {
  /**
   * @return the function being decorated, or null.
   */
  @Nullable
  PyFunction getTarget();

  /**
   * True if the annotating function is a builtin. Uses a stub, does not incur parsing, useful togeter with getName().
   * @see com.jetbrains.python.psi.PyElement#getName()
   */
  boolean isBuiltin();

}
