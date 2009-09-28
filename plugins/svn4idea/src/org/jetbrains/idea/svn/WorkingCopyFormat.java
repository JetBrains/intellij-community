package org.jetbrains.idea.svn;

/**
 * since not all constants are available from svnkit & constants are fixed
 */
public enum WorkingCopyFormat {
  ONE_DOT_THREE(4, false, false, false),
  ONE_DOT_FOUR(8, false, false, false),
  ONE_DOT_FIVE(9, true, true, false),
  ONE_DOT_SIX(10, true, true, true),
  UNKNOWN(0, false, false, false);

  private final int myFormat;
  private final boolean myChangelistSupport;
  private final boolean myMergeInfoSupport;
  private final boolean myTreeConflictSupport;

  private WorkingCopyFormat(final int format, boolean changelistSupport, boolean mergeInfoSupport, boolean treeConflictSupport) {
    myFormat = format;
    myChangelistSupport = changelistSupport;
    myMergeInfoSupport = mergeInfoSupport;
    myTreeConflictSupport = treeConflictSupport;
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
