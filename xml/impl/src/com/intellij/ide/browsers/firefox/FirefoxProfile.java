package com.intellij.ide.browsers.firefox;

import java.io.File;

/**
 * @author nik
 */
public class FirefoxProfile {
  private final String myName;
  private final String myPath;
  private final boolean myDefault;
  private final boolean myRelative;

  public FirefoxProfile(String name, String path, boolean aDefault, boolean relative) {
    myName = name;
    myPath = path;
    myDefault = aDefault;
    myRelative = relative;
  }

  public String getName() {
    return myName;
  }

  public String getPath() {
    return myPath;
  }

  public boolean isRelative() {
    return myRelative;
  }

  public boolean isDefault() {
    return myDefault;
  }

  public File getProfileDirectory(File profilesIniFile) {
    return myRelative ? new File(profilesIniFile.getParentFile(), myPath) : new File(myPath);
  }
}
