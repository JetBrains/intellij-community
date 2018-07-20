// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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

  @NotNull
  private final String myValue;

  PyRequirementRelation(@NotNull String value) {
    myValue = value;
  }

  /**
   * @return representation of this relation that is used in <a href="https://www.python.org/dev/peps/pep-0440/#version-specifiers">PEP-440</a>.
   */
  @NotNull
  public String getPresentableText() {
    return myValue;
  }
}
