/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.packaging.requirement;

import org.jetbrains.annotations.NotNull;

import static com.intellij.webcore.packaging.PackageVersionComparator.VERSION_COMPARATOR;

public class PyRequirementVersionSpec {

  @NotNull
  private final PyRequirementRelation myRelation;

  @NotNull
  private final String myVersion;

  public PyRequirementVersionSpec(@NotNull PyRequirementRelation relation, @NotNull String version) {
    myRelation = relation;
    myVersion = version;
  }

  @Override
  public String toString() {
    return myRelation + myVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PyRequirementVersionSpec spec = (PyRequirementVersionSpec)o;
    return myRelation == spec.myRelation && myVersion.equals(spec.myVersion);
  }

  @Override
  public int hashCode() {
    return 31 * myRelation.hashCode() + myVersion.hashCode();
  }

  @NotNull
  public PyRequirementRelation getRelation() {
    return myRelation;
  }

  @NotNull
  public String getVersion() {
    return myVersion;
  }

  public boolean matches(@NotNull String version) {
    switch (myRelation) {
      case LT:
        return VERSION_COMPARATOR.compare(version, myVersion) < 0;
      case LTE:
        return VERSION_COMPARATOR.compare(version, myVersion) <= 0;
      case GT:
        return VERSION_COMPARATOR.compare(version, myVersion) > 0;
      case GTE:
        return VERSION_COMPARATOR.compare(version, myVersion) >= 0;
      case EQ:
        return VERSION_COMPARATOR.compare(version, myVersion) == 0;
      case NE:
        return VERSION_COMPARATOR.compare(version, myVersion) != 0;
      case COMPATIBLE:
        return false; // TODO: implement matching version against compatible relation
      case STR_EQ:
        return version.equals(myVersion);
      default:
        return false;
    }
  }
}
