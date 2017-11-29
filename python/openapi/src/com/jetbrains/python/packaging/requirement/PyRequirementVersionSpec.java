/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.webcore.packaging.PackageVersionComparator.VERSION_COMPARATOR;

public class PyRequirementVersionSpec {

  @NotNull
  private final PyRequirementRelation myRelation;

  @Nullable
  private final PyRequirementVersion myParsedVersion;

  @NotNull
  private final String myVersion;

  public PyRequirementVersionSpec(@NotNull PyRequirementRelation relation, @NotNull PyRequirementVersion version) {
    myRelation = relation;
    myParsedVersion = version;
    myVersion = myParsedVersion.getPresentableText();
  }

  public PyRequirementVersionSpec(@NotNull String version) {
    myRelation = PyRequirementRelation.STR_EQ;
    myParsedVersion = null;
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
        Objects.requireNonNull(myParsedVersion);

        final Pair<String, String> publicAndLocalVersions = splitIntoPublicAndLocalVersions(myParsedVersion);
        final Pair<String, String> otherPublicAndLocalVersions = splitIntoPublicAndLocalVersions(version);
        final boolean publicVersionsAreSame =
          VERSION_COMPARATOR.compare(otherPublicAndLocalVersions.first, publicAndLocalVersions.first) == 0;

        return publicVersionsAreSame &&
               (publicAndLocalVersions.second.isEmpty() || otherPublicAndLocalVersions.second.equals(publicAndLocalVersions.second));
      case NE:
        return VERSION_COMPARATOR.compare(version, myVersion) != 0;
      case COMPATIBLE:
        Objects.requireNonNull(myParsedVersion);

        return new PyRequirementVersionSpec(PyRequirementRelation.GTE, myParsedVersion).matches(version) &&
               new PyRequirementVersionSpec(PyRequirementRelation.EQ, toEqPartOfCompatibleRelation(myParsedVersion)).matches(version);
      case STR_EQ:
        return version.equals(myVersion);
      default:
        return false;
    }
  }

  @NotNull
  private static Pair<String, String> splitIntoPublicAndLocalVersions(@NotNull PyRequirementVersion version) {
    final PyRequirementVersion withoutLocal =
      new PyRequirementVersion(version.getEpoch(), version.getRelease(), version.getPre(), version.getPost(), version.getDev(), null);

    return Pair.createNonNull(withoutLocal.getPresentableText(), StringUtil.notNullize(version.getLocal()));
  }

  @NotNull
  private static Pair<String, String> splitIntoPublicAndLocalVersions(@NotNull String version) {
    final String[] publicAndLocalVersions = version.split("\\+", 2);

    final String publicVersion = publicAndLocalVersions[0];
    final String localVersion = publicAndLocalVersions.length == 1 ? "" : publicAndLocalVersions[1];

    return Pair.createNonNull(publicVersion, localVersion);
  }

  @NotNull
  private static PyRequirementVersion toEqPartOfCompatibleRelation(@NotNull PyRequirementVersion version) {
    final String release = version.getRelease();
    final int lastPoint = release.lastIndexOf(".");

    if (lastPoint == -1) return version;

    return new PyRequirementVersion(version.getEpoch(), release.substring(0, lastPoint) + "*", null, null, null, null);
  }
}
