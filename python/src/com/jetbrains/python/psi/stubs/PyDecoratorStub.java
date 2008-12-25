package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.types.PyDecorator;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Sep 28, 2008
 */
public interface PyDecoratorStub extends StubElement<PyDecorator> {

  /**
   * @return name of the decorator after the "@".
   */
  String getName();

  /**
   * @return true if the decorator is a name from __builtins__
   */
  boolean isBuiltin();

  //PyFunction getTarget();
}
