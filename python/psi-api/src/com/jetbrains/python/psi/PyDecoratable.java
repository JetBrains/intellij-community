package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * TODO: Add description
 * User: dcheryasov
 * Date: Jul 8, 2010 3:48:14 AM
 */
public interface PyDecoratable {
  @Nullable
  PyDecoratorList getDecoratorList();
}
