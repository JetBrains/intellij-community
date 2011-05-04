package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Lists interesting features importable from __future__.
 * <code>.toString()</code> returns the Python name of the feature.
 * <br/>
 * User: dcheryasov
 */
public enum FutureFeature {
  GENERATORS("generators", 22, 23), // historical
  DIVISION("division", 22, 30),
  ABSOLUTE_IMPORT("absolute_import", 25, 27),
  WITH_STATEMENT("with_statement", 25, 26),
  PRINT_FUNCTION("print_function", 26, 30),
  UNICODE_LITERALS("unicode_literals", 26, 30),
  BARRY_AS_FLUFL("barry_as_FLUFL", 31, 39), // last as of CPython 3.2
  // NOTE: only add new features to the end unless you want to break existing stubs that rely on ordinal
  ;
  // TODO: link it to LanguageLevel
  private final String myName;
  private final int myProposed;
  private final int myIncluded;

  /**
   * @param name what is imported from __future__
   * @param proposed version in which the feature has become importable
   * @param included version in which the feature is included by default
   */
  FutureFeature(final @NotNull String name, final int proposed, final int included) {
    myName = name;
    myProposed = proposed;
    myIncluded = included;
  }

  @Override
  public String toString() {
    return myName;
  }

  public int getProposedVersion() {
    return myProposed;
  }

  public int getIncludedVersion() {
    return myIncluded;
  }

  public static final FutureFeature[] ALL = {
    GENERATORS, DIVISION, ABSOLUTE_IMPORT, WITH_STATEMENT, PRINT_FUNCTION, UNICODE_LITERALS, BARRY_AS_FLUFL
  };
}
