package com.intellij.ide.browsers;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily;

/*
 This class is a temporary solution that allows to use browser not listed in the BrowserFamily enum.
 TODO Vladimir Krivosheev: get rid of BrowserFamily enum usage, allow to create custom browsers at Web Browsers page in Settings (WEB-2093). 
 */
public class WebBrowser {

  public static final WebBrowser CHROME = createStandardBrowser(BrowserFamily.CHROME);
  public static final WebBrowser FIREFOX = createStandardBrowser(BrowserFamily.FIREFOX);
  public static final WebBrowser EXPLORER = createStandardBrowser(BrowserFamily.EXPLORER);
  public static final WebBrowser OPERA = createStandardBrowser(BrowserFamily.OPERA);
  public static final WebBrowser SAFARI = createStandardBrowser(BrowserFamily.SAFARI);

  private final @NotNull BrowserFamily myFamily;
  private final @NotNull String myName;
  private final @NotNull Icon myIcon;
  private final @NotNull Computable<String> myPathComputable;
  private final @NotNull String myBrowserNotFoundMessage;

  private WebBrowser(final @NotNull BrowserFamily family,
                     final @NotNull String name,
                     final @NotNull Icon icon,
                     final @NotNull NullableComputable<String> pathComputable,
                     final @NotNull String browserNotFoundMessage) {
    myFamily = family;
    myName = name;
    myIcon = icon;
    myPathComputable = pathComputable;
    myBrowserNotFoundMessage = browserNotFoundMessage;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public BrowserFamily getFamily() {
    return myFamily;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public String getPath() {
    return myPathComputable.compute();
  }

  @NotNull
  public String getBrowserNotFoundMessage() {
    return myBrowserNotFoundMessage;
  }

  @Nullable
  public BrowserSpecificSettings getBrowserSpecificSettings() {
    return null;
  }

  private static WebBrowser createStandardBrowser(final BrowserFamily family) {
    final String browserNotFoundMessage = IdeBundle.message("error.0.browser.path.not.specified", family.getName(),
                                                            CommonBundle.settingsActionPath());
    return new WebBrowser(family, family.getName(), family.getIcon(), new NullableComputable<String>() {
      @NotNull
      public String compute() {
        return BrowsersConfiguration.getInstance().getBrowserSettings(family).getPath();
      }
    }, browserNotFoundMessage) {
      @Nullable
      public BrowserSpecificSettings getBrowserSpecificSettings() {
        return BrowsersConfiguration.getInstance().getBrowserSettings(getFamily()).getBrowserSpecificSettings();
      }
    };
  }

  public static WebBrowser createCustomBrowser(final @NotNull BrowserFamily family,
                                               final @NotNull String name,
                                               final @NotNull Icon icon,
                                               final @NotNull NullableComputable<String> pathComputable,
                                               final @NotNull String browserNotFoundMessage) {
    return new WebBrowser(family, name, icon, pathComputable, browserNotFoundMessage);
  }
}