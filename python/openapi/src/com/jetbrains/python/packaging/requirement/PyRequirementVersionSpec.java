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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;

import static com.intellij.webcore.packaging.PackageVersionComparator.VERSION_COMPARATOR;

/**
 * @apiNote This class will be converted to interface in 2018.2.
 */
public class PyRequirementVersionSpec {

  @NotNull
  private final PyRequirementRelation myRelation;

  @Nullable
  private final PyRequirementVersion myParsedVersion;

  @NotNull
  private final String myVersion;

  @NotNull
  private final Comparator<String> myVersionComparator;

  /**
   * @deprecated Use {@link com.jetbrains.python.packaging.PyRequirement} instead.
   * This constructor will be removed in 2018.2.
   */
  public PyRequirementVersionSpec(@NotNull PyRequirementRelation relation, @NotNull PyRequirementVersion version) {
    this(relation, version, version.getPresentableText(), VERSION_COMPARATOR);
  }

  /**
   * @deprecated Use {@link com.jetbrains.python.packaging.PyRequirement} instead.
   * This constructor will be removed in 2018.2.
   */
  public PyRequirementVersionSpec(@NotNull String version) {
    this(PyRequirementRelation.STR_EQ, null, version, VERSION_COMPARATOR);
  }

  private PyRequirementVersionSpec(@NotNull PyRequirementRelation relation,
                                   @Nullable PyRequirementVersion parsedVersion,
                                   @NotNull String version,
                                   @NotNull Comparator<String> versionComparator) {
    myRelation = relation;
    myParsedVersion = parsedVersion;
    myVersion = version;
    myVersionComparator = versionComparator;
  }

  /**
   * @deprecated This method will be removed in 2018.2.
   */
  @NotNull
  @Deprecated
  public PyRequirementVersionSpec withVersionComparator(@NotNull Comparator<String> comparator) {
    return new PyRequirementVersionSpec(myRelation, myParsedVersion, myVersion, comparator);
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
        return myVersionComparator.compare(version, myVersion) < 0;
      case LTE:
        return myVersionComparator.compare(version, myVersion) <= 0;
      case GT:
        return myVersionComparator.compare(version, myVersion) > 0;
      case GTE:
        return myVersionComparator.compare(version, myVersion) >= 0;
      case EQ:
        Objects.requireNonNull(myParsedVersion);

        final Pair<String, String> publicAndLocalVersions = splitIntoPublicAndLocalVersions(myParsedVersion);
        final Pair<String, String> otherPublicAndLocalVersions = splitIntoPublicAndLocalVersions(version);
        final boolean publicVersionsAreSame =
          myVersionComparator.compare(otherPublicAndLocalVersions.first, publicAndLocalVersions.first) == 0;

        return publicVersionsAreSame &&
               (publicAndLocalVersions.second.isEmpty() || otherPublicAndLocalVersions.second.equals(publicAndLocalVersions.second));
      case NE:
        return myVersionComparator.compare(version, myVersion) != 0;
      case COMPATIBLE:
        Objects.requireNonNull(myParsedVersion);

        return new PyRequirementVersionSpec(PyRequirementRelation.GTE, myParsedVersion)
                 .withVersionComparator(myVersionComparator)
                 .matches(version) &&
               new PyRequirementVersionSpec(PyRequirementRelation.EQ, toEqPartOfCompatibleRelation(myParsedVersion))
                 .withVersionComparator(myVersionComparator)
                 .matches(version);
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
    final int lastPoint = release.lastIndexOf('.');

    if (lastPoint == -1) return version;

    return new PyRequirementVersion(version.getEpoch(), release.substring(0, lastPoint + 1) + "*", null, null, null, null);
  }
}
