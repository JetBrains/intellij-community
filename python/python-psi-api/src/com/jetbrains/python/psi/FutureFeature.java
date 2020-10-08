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
  DIVISION("division", LanguageLevel.PYTHON24, LanguageLevel.PYTHON30), // actually since 2.2
  ABSOLUTE_IMPORT("absolute_import", LanguageLevel.PYTHON25, LanguageLevel.PYTHON30),
  PRINT_FUNCTION("print_function", LanguageLevel.PYTHON26, LanguageLevel.PYTHON30),
  UNICODE_LITERALS("unicode_literals", LanguageLevel.PYTHON26, LanguageLevel.PYTHON30),
  ANNOTATIONS("annotations", LanguageLevel.PYTHON37, LanguageLevel.PYTHON310)
  // NOTE: only add new features to the end unless you want to break existing stubs that rely on ordinal
  ;

  @NotNull
  private final String myName;

  @NotNull
  private final LanguageLevel myOptionalVersion;

  @NotNull
  private final LanguageLevel myRequiredVersion;

  /**
   * @param name     what is imported from __future__
   * @param proposed version in which the feature has become importable
   * @param included version in which the feature is included by default
   */
  FutureFeature(@NotNull String name, @NotNull LanguageLevel proposed, @NotNull LanguageLevel included) {
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
   * @param level
   * @return true iff the feature can either be imported from __future__ at given level, or is already built-in.
   */
  public boolean availableAt(@NotNull LanguageLevel level) {
    return level.isAtLeast(myOptionalVersion);
  }

  /**
   * @param level
   * @return true iff the feature is already present (required) at given level, and there's no need to import it.
   */
  public boolean requiredAt(@NotNull LanguageLevel level) {
    return level.isAtLeast(myRequiredVersion);
  }
}
