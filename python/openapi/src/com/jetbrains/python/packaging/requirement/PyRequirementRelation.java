// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirement;

import org.jetbrains.annotations.NotNull;

/**
 * @see <a href="https://www.python.org/dev/peps/pep-0440/#version-specifiers">PEP-440</a>
 * @see PyRequirementVersionSpec
 * @see com.jetbrains.python.packaging.PyRequirement
 */
public enum PyRequirementRelation {

  LT("<"),
  LTE("<="),
  GT(">"),
  GTE(">="),
  EQ("=="),
  NE("!="),
  COMPATIBLE("~="),
  STR_EQ("===");

  private final @NotNull String myValue;

  PyRequirementRelation(@NotNull String value) {
    myValue = value;
  }

  /**
   * @return representation of this relation that is used in <a href="https://www.python.org/dev/peps/pep-0440/#version-specifiers">PEP-440</a>.
   */
  public @NotNull String getPresentableText() {
    return myValue;
  }
}
