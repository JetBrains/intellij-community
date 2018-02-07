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

import com.intellij.openapi.util.text.StringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes requirement version.
 * <p>
 * Based on
 * <a href="https://www.python.org/dev/peps/pep-0440/#normalization">https://www.python.org/dev/peps/pep-0440/#normalization</a>
 * and
 * <a href="https://www.python.org/dev/peps/pep-0440/#summary-of-permitted-suffixes-and-relative-ordering">https://www.python.org/dev/peps/pep-0440/#summary-of-permitted-suffixes-and-relative-ordering</a>.
 *
 * @deprecated Use {@link com.jetbrains.python.packaging.PyRequirement} instead.
 * This class will be removed in 2018.2.
 */
@Deprecated
public final class PyRequirementVersionNormalizer {

  @NotNull
  private static final String EPOCH_GROUP = "epoch";

  @NotNull
  private static final String RELEASE_GROUP = "release";

  @NotNull
  private static final String PRE_RELEASE_TYPE_GROUP = "pretype";

  @NotNull
  private static final String PRE_RELEASE_NUMBER_GROUP = "prenumber";

  @NotNull
  private static final String POST_RELEASE_TYPE_GROUP = "posttype";

  @NotNull
  private static final String POST_RELEASE_NUMBER_GROUP = "postnumber";

  @NotNull
  private static final String IMPLICIT_POST_RELEASE_NUMBER_GROUP = "implicitpostnumber";

  @NotNull
  private static final String DEV_RELEASE_TYPE_GROUP = "devtype";

  @NotNull
  private static final String DEV_RELEASE_NUMBER_GROUP = "devnumber";

  @NotNull
  private static final String LOCAL_VERSION_GROUP = "local";

  @NotNull
  private static final String SEP_REGEXP = "([\\.\\-_])?";

  @NotNull
  private static final String EPOCH_REGEXP = "(?<" + EPOCH_GROUP + ">\\d+!)?";

  @NotNull
  private static final String RELEASE_REGEXP = "(?<" + RELEASE_GROUP + ">(\\d+(\\.\\d+)*)|(\\d+\\.(\\d+\\.)*\\*))";

  @NotNull
  private static final String PRE_RELEASE_REGEXP =
    "(" +
    SEP_REGEXP +
    "(?<" + PRE_RELEASE_TYPE_GROUP + ">a|alpha|b|beta|rc|c|pre|preview)" +
    "(" + SEP_REGEXP + "(?<" + PRE_RELEASE_NUMBER_GROUP + ">\\d+))?" +
    ")?";

  @NotNull
  private static final String POST_RELEASE_REGEXP =
    "(" +
    "(" + SEP_REGEXP + "(?<" + POST_RELEASE_TYPE_GROUP + ">post|rev|r)(" + SEP_REGEXP + "(?<" + POST_RELEASE_NUMBER_GROUP + ">\\d+))?)" +
    "|" +
    "(-(?<" + IMPLICIT_POST_RELEASE_NUMBER_GROUP + ">\\d+))" +
    ")?";

  @NotNull
  private static final String DEV_RELEASE_REGEXP =
    "(" +
    SEP_REGEXP + "(?<" + DEV_RELEASE_TYPE_GROUP + ">dev)(?<" + DEV_RELEASE_NUMBER_GROUP + ">\\d+)?" +
    ")?";

  @NotNull
  private static final String LOCAL_VERSION_REGEXP = "(?<" + LOCAL_VERSION_GROUP + ">\\+[a-z0-9]([a-z0-9\\._-]*[a-z0-9])?)?";

  @NotNull
  private static final Pattern VERSION = Pattern.compile(
    "^" +
    "v?" +
    EPOCH_REGEXP +
    RELEASE_REGEXP +
    PRE_RELEASE_REGEXP +
    POST_RELEASE_REGEXP +
    DEV_RELEASE_REGEXP +
    LOCAL_VERSION_REGEXP +
    "$",
    Pattern.CASE_INSENSITIVE);

  @Nullable
  public static PyRequirementVersion normalize(@NotNull String version) {
    final Matcher matcher = VERSION.matcher(version);
    if (matcher.matches()) {
      return new PyRequirementVersion(
        normalizeEpoch(matcher),
        normalizeRelease(matcher),
        normalizePre(matcher),
        normalizePost(matcher),
        normalizeDev(matcher),
        normalizeLocal(matcher)
      );
    }

    return null;
  }

  @Nullable
  private static String normalizeEpoch(@NotNull Matcher matcher) {
    final String epoch = matcher.group(EPOCH_GROUP);
    if (epoch != null) {
      return normalizeNumber(epoch.substring(0, epoch.length() - 1));
    }

    return null;
  }

  @NotNull
  private static String normalizeRelease(@NotNull Matcher matcher) {
    return StreamEx
      .of(StringUtil.tokenize(matcher.group(RELEASE_GROUP), ".").iterator())
      .map(releasePart -> releasePart.equals("*") ? "*" : normalizeNumber(releasePart))
      .joining(".");
  }

  @Nullable
  private static String normalizePre(@NotNull Matcher matcher) {
    final String preReleaseType = matcher.group(PRE_RELEASE_TYPE_GROUP);
    if (preReleaseType != null) {
      final String preReleaseNumber = matcher.group(PRE_RELEASE_NUMBER_GROUP);
      final String normalizedPreReleaseNumber = preReleaseNumber == null ? "0" : normalizeNumber(preReleaseNumber);

      return normalizePreReleaseType(preReleaseType) + normalizedPreReleaseNumber;
    }

    return null;
  }

  @Nullable
  private static String normalizePost(@NotNull Matcher matcher) {
    final String postReleaseType = matcher.group(POST_RELEASE_TYPE_GROUP);
    if (postReleaseType != null) {
      final String postReleaseNumber = matcher.group(POST_RELEASE_NUMBER_GROUP);
      final String normalizedPostReleaseNumber = postReleaseNumber == null ? "0" : normalizeNumber(postReleaseNumber);

      return "post" + normalizeNumber(normalizedPostReleaseNumber);
    }

    final String implicitPostReleaseNumber = matcher.group(IMPLICIT_POST_RELEASE_NUMBER_GROUP);
    if (implicitPostReleaseNumber != null) {
      return "post" + normalizeNumber(implicitPostReleaseNumber);
    }

    return null;
  }

  @Nullable
  private static String normalizeDev(@NotNull Matcher matcher) {
    if (matcher.group(DEV_RELEASE_TYPE_GROUP) != null) {
      final String devReleaseNumber = matcher.group(DEV_RELEASE_NUMBER_GROUP);
      final String normalizedDevReleaseNumber = devReleaseNumber == null ? "0" : normalizeNumber(devReleaseNumber);

      return "dev" + normalizedDevReleaseNumber;
    }

    return null;
  }

  @Nullable
  private static String normalizeLocal(@NotNull Matcher matcher) {
    final String localVersion = matcher.group(LOCAL_VERSION_GROUP);
    if (localVersion != null) {
      return localVersion.substring(1).replaceAll("[-_]", ".");
    }

    return null;
  }

  @NotNull
  private static String normalizeNumber(@NotNull String number) {
    return new BigInteger(number).toString();
  }

  @NotNull
  private static String normalizePreReleaseType(@NotNull String preReleaseType) {
    if (preReleaseType.equalsIgnoreCase("a") || preReleaseType.equalsIgnoreCase("alpha")) {
      return "a";
    }
    else if (preReleaseType.equalsIgnoreCase("b") || preReleaseType.equalsIgnoreCase("beta")) {
      return "b";
    }
    else {
      return "rc";
    }
  }
}
