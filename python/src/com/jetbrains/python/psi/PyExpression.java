package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describes a generalized expression, possibly typed.
 *
 * @author yole
 */
public interface PyExpression extends PyElement {
  PyExpression[] EMPTY_ARRAY = new PyExpression[0];

  @Nullable
  PyType getType();
}
