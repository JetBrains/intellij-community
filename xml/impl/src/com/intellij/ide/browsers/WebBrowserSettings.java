package com.intellij.ide.browsers;

import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class WebBrowserSettings {
  private final String myPath;
  private final boolean myActive;
  private final BrowserSpecificSettings myBrowserSpecificSettings;

  public WebBrowserSettings(String path, boolean active, BrowserSpecificSettings browserSpecificSettings) {
    myPath = path;
    myActive = active;
    myBrowserSpecificSettings = browserSpecificSettings;
  }

  public String getPath() {
    return myPath;
  }

  public boolean isActive() {
    return myActive;
  }

  @Nullable
  public BrowserSpecificSettings getBrowserSpecificSettings() {
    return myBrowserSpecificSettings;
  }
}
