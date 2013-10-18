package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Base class for elements that have a qualified name (classes and functions).
 *
 * @author yole
 */
public interface PyQualifiedNameOwner extends PyElement {
  /**
   * Returns the qualified name of the element.
   *
   * @return the qualified name of the element, or null if the element doesn't have a name (for example, it is a lambda expression) or
   * is contained inside an element that doesn't have a qualified name.
   */
  @Nullable
  String getQualifiedName();
}
