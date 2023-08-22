// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;


public interface PySequenceExpression extends PyExpression{
  PyExpression @NotNull [] getElements();

  /**
   * Calling {@link #getElements()} may take too much time in case of large literals with thousands of elements. If you only need to
   * know whether collection is empty, use this method instead.
   *
   * @return true if sequence expression contains no elements
   */
  boolean isEmpty();
}
