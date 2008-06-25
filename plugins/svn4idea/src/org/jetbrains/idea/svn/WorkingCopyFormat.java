package org.jetbrains.idea.svn;

/**
 * since not all constants are available from svnkit & constants are fixed
 */
public enum WorkingCopyFormat {
  ONE_DOT_THREE(4),
  ONE_DOT_FOUR(8),
  ONE_DOT_FIVE(9),
  UNKNOWN(0);

  private final int myFormat;

  private WorkingCopyFormat(final int format) {
    myFormat = format;
  }

  public static WorkingCopyFormat getInstance(final int value) {
    if (ONE_DOT_FIVE.getFormat() == value) {
      return ONE_DOT_FIVE;
    } else if (ONE_DOT_FOUR.getFormat() == value) {
      return ONE_DOT_FOUR;
    } else if (ONE_DOT_THREE.getFormat() == value) {
      return ONE_DOT_THREE;
    }
    return UNKNOWN;
  }

  public static WorkingCopyFormat getInstance(final String updateOption) {
    if (SvnConfiguration.UPGRADE_AUTO_15.equals(updateOption)) {
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
    if (ONE_DOT_FIVE.equals(this)) {
      return SvnConfiguration.UPGRADE_AUTO_15;
    } else if (ONE_DOT_FOUR.equals(this)) {
      return SvnConfiguration.UPGRADE_AUTO;
    }
    return SvnConfiguration.UPGRADE_NONE;
  }
}
