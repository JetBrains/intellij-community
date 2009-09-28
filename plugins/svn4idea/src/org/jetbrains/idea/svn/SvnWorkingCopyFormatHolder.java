package org.jetbrains.idea.svn;

public class SvnWorkingCopyFormatHolder {
  private static final ThreadLocal<WorkingCopyFormat> myPresetFormat = new ThreadLocal<WorkingCopyFormat>();

  private SvnWorkingCopyFormatHolder() {
  }

  public static WorkingCopyFormat getPresetFormat() {
    return myPresetFormat.get();
  }

  public static void setPresetFormat(final WorkingCopyFormat value) {
    myPresetFormat.set(value);
  }
}
