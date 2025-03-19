// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.openapi.util.NlsSafe;
import com.jetbrains.python.ast.PyAstQualifiedNameOwner;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for elements that have a qualified name (classes and functions).
 */
public interface PyQualifiedNameOwner extends PyAstQualifiedNameOwner, PyElement {
  /**
   * Returns the qualified name of the element.
   *
   * @return the qualified name of the element, or null if the element doesn't have a name (for example, it is a lambda expression) or
   * is contained inside an element that doesn't have a qualified name.
   */
  @Nullable
  @NlsSafe String getQualifiedName();
}
