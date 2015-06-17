/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;

/**
 * since not all constants are available from svnkit & constants are fixed
 */
public enum WorkingCopyFormat {

  ONE_DOT_THREE(4, false, false, false, SvnBundle.message("dialog.show.svn.map.table.version13.text"), new Version(1, 3, 0)),
  ONE_DOT_FOUR(8, false, false, false, SvnBundle.message("dialog.show.svn.map.table.version14.text"), new Version(1, 4, 0)),
  ONE_DOT_FIVE(9, true, true, false, SvnBundle.message("dialog.show.svn.map.table.version15.text"), new Version(1, 5, 0)),
  ONE_DOT_SIX(10, true, true, true, SvnBundle.message("dialog.show.svn.map.table.version16.text"), new Version(1, 6, 0)),
  ONE_DOT_SEVEN(12, true, true, true, SvnBundle.message("dialog.show.svn.map.table.version17.text"), new Version(1, 7, 0)),
  ONE_DOT_EIGHT(12, true, true, true, SvnBundle.message("dialog.show.svn.map.table.version18.text"), new Version(1, 8, 0)),
  UNKNOWN(0, false, false, false, "unknown", new Version(0, 0, 0));

  public static final int INTERNAL_FORMAT_17 = 29;
  public static final int INTERNAL_FORMAT_18 = 31;
  private static final Version ONE_DOT_NINE_VERSION = new Version(1, 9, 0);

  private final int myFormat;
  private final boolean myChangelistSupport;
  private final boolean myMergeInfoSupport;
  private final boolean myTreeConflictSupport;
  private final String myName;
  @NotNull
  private final Version myVersion;

  private WorkingCopyFormat(final int format,
                            boolean changelistSupport,
                            boolean mergeInfoSupport,
                            boolean treeConflictSupport,
                            String name,
                            @NotNull Version version) {
    myFormat = format;
    myChangelistSupport = changelistSupport;
    myMergeInfoSupport = mergeInfoSupport;
    myTreeConflictSupport = treeConflictSupport;
    myName = name;
    myVersion = version;
  }

  public boolean supportsChangelists() {
    return myChangelistSupport;
  }

  public boolean supportsMergeInfo() {
    return myMergeInfoSupport;
  }

  public boolean supportsTreeConflicts() {
    return myTreeConflictSupport;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public Version getVersion() {
    return myVersion;
  }

  @NotNull
  public static WorkingCopyFormat getInstance(final int value) {
    // somewhy 1.7 wc format can also be 29
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
    WorkingCopyFormat result = UNKNOWN;

    if (version.compareTo(ONE_DOT_NINE_VERSION) >= 0) {
      result = ONE_DOT_EIGHT;
    }
    else {
      for (WorkingCopyFormat format : WorkingCopyFormat.values()) {
        if (format.getVersion().is(version.major, version.minor)) {
          result = format;
          break;
        }
      }
    }

    return result;
  }

  @Override
  public String toString() {
    return getName();
  }
}
