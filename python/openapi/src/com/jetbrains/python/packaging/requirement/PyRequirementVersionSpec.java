// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirement;

import org.jetbrains.annotations.NotNull;

/**
 * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
 * @see com.jetbrains.python.packaging.PyRequirement
 * @see PyRequirementRelation
 */
public interface PyRequirementVersionSpec {

  @NotNull
  PyRequirementRelation getRelation();

  @NotNull
  String getVersion();

  /**
   * @param version version to check
   * @return true if given version satisfies this version spec.
   */
  boolean matches(@NotNull String version);

  /**
   * @return concatenated representation of relation and version so it could be easily parsed or displayed.
   */
  default @NotNull String getPresentableText() {
    return getRelation().getPresentableText() + getVersion();
  }
}
