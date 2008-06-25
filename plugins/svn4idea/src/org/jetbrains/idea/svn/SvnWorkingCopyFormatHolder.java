package org.jetbrains.idea.svn;

public class SvnWorkingCopyFormatHolder {
  private static String ourFormatGlobalDefault;
  private static ThreadLocal<String> myPresetFormat = new ThreadLocal<String>();
  private static ThreadLocal<String> myRecentlySelected = new ThreadLocal<String>();

  private SvnWorkingCopyFormatHolder() {
  }

  public static String getPresetFormat() {
    return myPresetFormat.get();
  }

  public static void setPresetFormat(final String value) {
    myPresetFormat.set(value);
    synchronized (SvnWorkingCopyFormatHolder.class) {
      ourFormatGlobalDefault = value;
    }
  }

  public static synchronized String getFormatGlobalDefault() {
    return ourFormatGlobalDefault;
  }

  public static void setRecentlySelected(final String value) {
    myRecentlySelected.set(value);
    synchronized (SvnWorkingCopyFormatHolder.class) {
      ourFormatGlobalDefault = value;
    }
  }

  public static String getRecentlySelected() {
    return myRecentlySelected.get();
  }
}
