// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest;

import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.ApiStatus;

/**
 * User : ktisha
 */
@ApiStatus.Internal
public final class PyDoctestTokenTypes {
  public static final PyElementType DOC_REFERENCE = new PyElementType("DOC_REFERENCE", node -> new PyDoctestReferenceExpression(node));
  public static final PyElementType DOTS = new PyElementType("DOTS");

  private PyDoctestTokenTypes() {
  }
}
