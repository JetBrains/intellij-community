package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * The "if" part of list comprehensions and generators.
 * User: dcheryasov
 * Date: Jul 31, 2008
 */
public interface ComprhIfComponent extends ComprehensionComponent {
  @Nullable  
  PyExpression getTest();
}
