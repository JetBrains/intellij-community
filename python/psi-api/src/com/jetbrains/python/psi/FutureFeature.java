/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Lists interesting features importable from __future__.
 * {@code .toString()} returns the Python name of the feature.
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
  ANNOTATIONS("annotations", 37, 40)
  // NOTE: only add new features to the end unless you want to break existing stubs that rely on ordinal
  ;
  // TODO: link it to LanguageLevel
  private final String myName;
  private final int myOptionalVersion;
  private final int myRequiredVersion;

  /**
   * @param name what is imported from __future__
   * @param proposed version in which the feature has become importable
   * @param included version in which the feature is included by default
   */
  FutureFeature(final @NotNull String name, final int proposed, final int included) {
    myName = name;
    myOptionalVersion = proposed;
    myRequiredVersion = included;
  }

  /**
   * @return the Python importable name of the feature.
   */
  @Override
  public String toString() {
    return myName;
  }

  /**
   * @return Version since which it is possible to import the feature from __future__
   */
  public int getOptionalVersion() {
    return myOptionalVersion;
  }

  /**
   * @return Version since which the feature is built into the language (required from the language).
   */
  public int getRequiredVersion() {
    return myRequiredVersion;
  }

  /**
   * @param level
   * @return true iff the feature can either be imported from __future__ at given level, or is already built-in.
   */
  public boolean availableAt(LanguageLevel level) {
    return level.getVersion() >= myOptionalVersion;
  }

  /**
   * @param level
   * @return true iff the feature is already present (required) at given level, and there's no need to import it.
   */
  public boolean requiredAt(LanguageLevel level) {
    return level.getVersion() >= myRequiredVersion;
  }

  public static final FutureFeature[] ALL = FutureFeature.values();
}
