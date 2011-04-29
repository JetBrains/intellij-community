package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Lists interesting features importable from __future__.
 * <code>.toString()</code> returns the Python name of the feature.
 * <br/>
 * User: dcheryasov
 */
public enum FutureFeature {
  GENERATORS("generators"), // historical: 2.2 -> 2.3
  DIVISION("division"),     // 2.2 -> 3.0
  ABSOLUTE_IMPORT("absolute_import"), // 2.5 -> 2.7
  WITH_STATEMENT("with_statement"), // 2.5 -> 2.6
  PRINT_FUNCTION("print_function"), // 2.6 -> 3.0
  UNICODE_LITERALS("unicode_literals"), // 2.6 -> 3.0
  ;
  // TODO: link it to LanguageLevel
  private final String myName;

  FutureFeature(final @NotNull String name) {
    myName = name;
  }

  @Override
  public String toString() {
    return myName;
  }
}
