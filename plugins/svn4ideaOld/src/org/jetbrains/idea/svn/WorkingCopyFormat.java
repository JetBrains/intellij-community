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

/**
 * since not all constants are available from svnkit & constants are fixed
 */
public enum WorkingCopyFormat {
  ONE_DOT_THREE(4, false, false, false, SvnBundle.message("dialog.show.svn.map.table.version13.text")),
  ONE_DOT_FOUR(8, false, false, false, SvnBundle.message("dialog.show.svn.map.table.version14.text")),
  ONE_DOT_FIVE(9, true, true, false, SvnBundle.message("dialog.show.svn.map.table.version15.text")),
  ONE_DOT_SIX(10, true, true, true, SvnBundle.message("dialog.show.svn.map.table.version16.text")),
  UNKNOWN(0, false, false, false, "unknown");

  private final int myFormat;
  private final boolean myChangelistSupport;
  private final boolean myMergeInfoSupport;
  private final boolean myTreeConflictSupport;
  private final String myName;

  private WorkingCopyFormat(final int format, boolean changelistSupport, boolean mergeInfoSupport, boolean treeConflictSupport, String name) {
    myFormat = format;
    myChangelistSupport = changelistSupport;
    myMergeInfoSupport = mergeInfoSupport;
    myTreeConflictSupport = treeConflictSupport;
    myName = name;
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

  public static WorkingCopyFormat getInstance(final int value) {
    if (ONE_DOT_FIVE.getFormat() == value) {
      return ONE_DOT_FIVE;
    } else if (ONE_DOT_FOUR.getFormat() == value) {
      return ONE_DOT_FOUR;
    } else if (ONE_DOT_THREE.getFormat() == value) {
      return ONE_DOT_THREE;
    } else if (ONE_DOT_SIX.getFormat() == value) {
      return ONE_DOT_SIX;
    }
    return UNKNOWN;
  }

  public static WorkingCopyFormat getInstance(final String updateOption) {
    if (SvnConfiguration.UPGRADE_AUTO_16.equals(updateOption)) {
      return ONE_DOT_SIX;
    } else if (SvnConfiguration.UPGRADE_AUTO_15.equals(updateOption)) {
      return ONE_DOT_FIVE;
    } else if (SvnConfiguration.UPGRADE_AUTO.equals(updateOption)) {
      return ONE_DOT_FOUR;
    }
    return ONE_DOT_THREE;
  }

  public int getFormat() {
    return myFormat;
  }

  public String getOption() {
    if (ONE_DOT_SIX.equals(this)) {
      return SvnConfiguration.UPGRADE_AUTO_16;
    } else if (ONE_DOT_FIVE.equals(this)) {
      return SvnConfiguration.UPGRADE_AUTO_15;
    } else if (ONE_DOT_FOUR.equals(this)) {
      return SvnConfiguration.UPGRADE_AUTO;
    }
    return SvnConfiguration.UPGRADE_NONE;
  }
}
