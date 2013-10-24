package com.intellij.ide.browsers;

import org.jetbrains.annotations.NotNull;

import static com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily;

public final class WebBrowser {
  private final BrowserFamily myFamily;
  private final String myName;

  public WebBrowser(@NotNull BrowserFamily browserFamily) {
    this(browserFamily.getName(), browserFamily);
  }

  public WebBrowser(@NotNull String name, @NotNull BrowserFamily browserFamily) {
    myFamily = browserFamily;
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public BrowserFamily getFamily() {
    return myFamily;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    WebBrowser browser = (WebBrowser)o;
    if (myFamily != browser.myFamily) {
      return false;
    }
    if (!myName.equals(browser.myName)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = myFamily.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }
}