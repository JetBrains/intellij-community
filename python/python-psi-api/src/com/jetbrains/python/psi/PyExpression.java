// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

/**
 * Describes a generalized expression, possibly typed.
 */
public interface PyExpression extends PyTypedElement {
  PyExpression[] EMPTY_ARRAY = new PyExpression[0];
}
