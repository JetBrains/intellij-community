// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  default String getPresentableText() {
    return getRelation().getPresentableText() + getVersion();
  }
}
