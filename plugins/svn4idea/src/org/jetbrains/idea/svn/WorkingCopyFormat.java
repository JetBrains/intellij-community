// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

import static org.jetbrains.idea.svn.SvnBundle.message;

public enum WorkingCopyFormat {
  ONE_DOT_THREE(4, new Version(1, 3, 0)),
  ONE_DOT_FOUR(8, new Version(1, 4, 0)),
  ONE_DOT_FIVE(9, new Version(1, 5, 0)),
  ONE_DOT_SIX(10, new Version(1, 6, 0)),
  ONE_DOT_SEVEN(12, new Version(1, 7, 0)),
  ONE_DOT_EIGHT(12, new Version(1, 8, 0)),
  UNKNOWN(0, new Version(0, 0, 0));

  public static final int INTERNAL_FORMAT_17 = 29;
  public static final int INTERNAL_FORMAT_18 = 31;
  private static final Version ONE_DOT_NINE_VERSION = new Version(1, 9, 0);

  private final int myFormat;
  @NotNull private final Version myVersion;

  WorkingCopyFormat(int format, @NotNull Version version) {
    myFormat = format;
    myVersion = version;
  }

  public boolean supportsChangelists() {
    return isOrGreater(ONE_DOT_FIVE);
  }

  public boolean supportsMergeInfo() {
    return isOrGreater(ONE_DOT_FIVE);
  }

  public @NlsSafe @NotNull String getName() {
    return myVersion.toCompactString();
  }

  public @Nls @NotNull String getDisplayName() {
    return this == UNKNOWN ? message("label.working.copy.format.unknown") : getName();
  }

  @NotNull
  public Version getVersion() {
    return myVersion;
  }

  @NotNull
  public static WorkingCopyFormat getInstance(int value) {
    if (INTERNAL_FORMAT_17 == value) {
      return ONE_DOT_SEVEN;
    } else if (INTERNAL_FORMAT_18 == value) {
      return ONE_DOT_EIGHT;
    } else if (ONE_DOT_FIVE.getFormat() == value) {
      return ONE_DOT_FIVE;
    } else if (ONE_DOT_FOUR.getFormat() == value) {
      return ONE_DOT_FOUR;
    } else if (ONE_DOT_THREE.getFormat() == value) {
      return ONE_DOT_THREE;
    } else if (ONE_DOT_SIX.getFormat() == value) {
      return ONE_DOT_SIX;
    } else if (ONE_DOT_SEVEN.getFormat() == value) {
      return ONE_DOT_SEVEN;
    }
    return UNKNOWN;
  }

  public int getFormat() {
    return myFormat;
  }

  public boolean isOrGreater(@NotNull WorkingCopyFormat format) {
    return myVersion.isOrGreaterThan(format.getVersion().major, format.getVersion().minor);
  }

  public boolean less(@NotNull WorkingCopyFormat format) {
    return myVersion.lessThan(format.getVersion().major, format.getVersion().minor);
  }

  @NotNull
  public static WorkingCopyFormat from(@NotNull Version version) {
    return version.compareTo(ONE_DOT_NINE_VERSION) >= 0
           ? ONE_DOT_EIGHT
           : Stream.of(values())
             .filter(format -> format.getVersion().is(version.major, version.minor))
             .findFirst()
             .orElse(UNKNOWN);
  }

  @Override
  public String toString() {
    return getName();
  }
}
