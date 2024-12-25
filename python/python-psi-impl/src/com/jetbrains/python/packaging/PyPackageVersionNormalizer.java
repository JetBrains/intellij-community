// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

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
 */
public final class PyPackageVersionNormalizer {

  private static final @NotNull String EPOCH_GROUP = "epoch";

  private static final @NotNull String RELEASE_GROUP = "release";

  private static final @NotNull String PRE_RELEASE_TYPE_GROUP = "pretype";

  private static final @NotNull String PRE_RELEASE_NUMBER_GROUP = "prenumber";

  private static final @NotNull String POST_RELEASE_TYPE_GROUP = "posttype";

  private static final @NotNull String POST_RELEASE_NUMBER_GROUP = "postnumber";

  private static final @NotNull String IMPLICIT_POST_RELEASE_NUMBER_GROUP = "implicitpostnumber";

  private static final @NotNull String DEV_RELEASE_TYPE_GROUP = "devtype";

  private static final @NotNull String DEV_RELEASE_NUMBER_GROUP = "devnumber";

  private static final @NotNull String LOCAL_VERSION_GROUP = "local";

  private static final @NotNull String SEP_REGEXP = "([\\.\\-_])?";

  private static final @NotNull String EPOCH_REGEXP = "(?<" + EPOCH_GROUP + ">\\d+!)?";

  private static final @NotNull String RELEASE_REGEXP = "(?<" + RELEASE_GROUP + ">(\\d+(\\.\\d+)*)|(\\d+\\.(\\d+\\.)*\\*))";

  private static final @NotNull String PRE_RELEASE_REGEXP =
    "(" +
    SEP_REGEXP +
    "(?<" + PRE_RELEASE_TYPE_GROUP + ">a|alpha|b|beta|rc|c|pre|preview)" +
    "(" + SEP_REGEXP + "(?<" + PRE_RELEASE_NUMBER_GROUP + ">\\d+))?" +
    ")?";

  private static final @NotNull String POST_RELEASE_REGEXP =
    "(" +
    "(" + SEP_REGEXP + "(?<" + POST_RELEASE_TYPE_GROUP + ">post|rev|r)(" + SEP_REGEXP + "(?<" + POST_RELEASE_NUMBER_GROUP + ">\\d+))?)" +
    "|" +
    "(-(?<" + IMPLICIT_POST_RELEASE_NUMBER_GROUP + ">\\d+))" +
    ")?";

  private static final @NotNull String DEV_RELEASE_REGEXP =
    "(" +
    SEP_REGEXP + "(?<" + DEV_RELEASE_TYPE_GROUP + ">dev)(?<" + DEV_RELEASE_NUMBER_GROUP + ">\\d+)?" +
    ")?";

  private static final @NotNull String LOCAL_VERSION_REGEXP = "(?<" + LOCAL_VERSION_GROUP + ">\\+[a-z0-9]([a-z0-9\\._-]*[a-z0-9])?)?";

  private static final @NotNull Pattern VERSION = Pattern.compile(
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

  public static @Nullable PyPackageVersion normalize(@NotNull String version) {
    final Matcher matcher = VERSION.matcher(version);
    if (matcher.matches()) {
      return new PyPackageVersion(
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

  private static @Nullable String normalizeEpoch(@NotNull Matcher matcher) {
    final String epoch = matcher.group(EPOCH_GROUP);
    if (epoch != null) {
      return normalizeNumber(epoch.substring(0, epoch.length() - 1));
    }

    return null;
  }

  private static @NotNull String normalizeRelease(@NotNull Matcher matcher) {
    return StreamEx
      .of(StringUtil.tokenize(matcher.group(RELEASE_GROUP), ".").iterator())
      .map(releasePart -> releasePart.equals("*") ? "*" : normalizeNumber(releasePart))
      .joining(".");
  }

  private static @Nullable String normalizePre(@NotNull Matcher matcher) {
    final String preReleaseType = matcher.group(PRE_RELEASE_TYPE_GROUP);
    if (preReleaseType != null) {
      final String preReleaseNumber = matcher.group(PRE_RELEASE_NUMBER_GROUP);
      final String normalizedPreReleaseNumber = preReleaseNumber == null ? "0" : normalizeNumber(preReleaseNumber);

      return normalizePreReleaseType(preReleaseType) + normalizedPreReleaseNumber;
    }

    return null;
  }

  private static @Nullable String normalizePost(@NotNull Matcher matcher) {
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

  private static @Nullable String normalizeDev(@NotNull Matcher matcher) {
    if (matcher.group(DEV_RELEASE_TYPE_GROUP) != null) {
      final String devReleaseNumber = matcher.group(DEV_RELEASE_NUMBER_GROUP);
      final String normalizedDevReleaseNumber = devReleaseNumber == null ? "0" : normalizeNumber(devReleaseNumber);

      return "dev" + normalizedDevReleaseNumber;
    }

    return null;
  }

  private static @Nullable String normalizeLocal(@NotNull Matcher matcher) {
    final String localVersion = matcher.group(LOCAL_VERSION_GROUP);
    if (localVersion != null) {
      return localVersion.substring(1).replaceAll("[-_]", ".");
    }

    return null;
  }

  private static @NotNull String normalizeNumber(@NotNull String number) {
    return new BigInteger(number).toString();
  }

  private static @NotNull String normalizePreReleaseType(@NotNull String preReleaseType) {
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
