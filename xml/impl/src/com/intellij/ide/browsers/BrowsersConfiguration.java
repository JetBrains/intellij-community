/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.browsers;

import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.chrome.ChromeSettings;
import com.intellij.ide.browsers.firefox.FirefoxSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xml.XmlBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author spleaner
 */
@State(name = "WebBrowsersConfiguration", storages = {@Storage( file = "$APP_CONFIG$/browsers.xml")})
public class BrowsersConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.BrowsersConfiguration");

  public static enum BrowserFamily {
    EXPLORER(XmlBundle.message("browsers.explorer"), "iexplore", null, null, IconLoader.getIcon("/xml/browsers/explorer16.png")),
    SAFARI(XmlBundle.message("browsers.safari"), "safari", null, "Safari", IconLoader.getIcon("/xml/browsers/safari16.png")),
    OPERA(XmlBundle.message("browsers.opera"), "opera", "opera", "Opera", IconLoader.getIcon("/xml/browsers/opera16.png")),
    FIREFOX(XmlBundle.message("browsers.firefox"), "firefox", "firefox", "Firefox", IconLoader.getIcon("/xml/browsers/firefox16.png")) {
      @Override
      public BrowserSpecificSettings createBrowserSpecificSettings() {
        return new FirefoxSettings();
      }
    },
    CHROME(XmlBundle.message("browsers.chrome"), getWindowsPathToChrome(), "google-chrome", "Google Chrome", IconLoader.getIcon("/xml/browsers/chrome16.png")) {
      @Override
      public BrowserSpecificSettings createBrowserSpecificSettings() {
        return new ChromeSettings();
      }
    };

    private final String myName;
    private final String myWindowsPath;
    private final String myLinuxPath;
    private final String myMacPath;
    private final Icon myIcon;

    BrowserFamily(final String name, @NonNls final String windowsPath, @NonNls final String linuxPath, @NonNls final String macPath, final Icon icon) {
      myName = name;
      myWindowsPath = windowsPath;
      myLinuxPath = linuxPath;
      myMacPath = macPath;
      myIcon = icon;
    }

    @Nullable
    public BrowserSpecificSettings createBrowserSpecificSettings() {
      return null;
    }

    @Nullable
    public String getExecutionPath() {
      if (SystemInfo.isWindows) {
        return myWindowsPath;
      }
      else if (SystemInfo.isLinux) {
        return myLinuxPath;
      }
      else if (SystemInfo.isMac) {
        return myMacPath;
      }

      return null;
    }

    public String getName() {
      return myName;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  private final Map<BrowserFamily, WebBrowserSettings> myBrowserToSettingsMap = new HashMap<BrowserFamily, WebBrowserSettings>();

  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element getState() {
    @NonNls Element element = new Element("WebBrowsersConfiguration");
    for (BrowserFamily browserFamily : myBrowserToSettingsMap.keySet()) {
      final Element browser = new Element("browser");
      browser.setAttribute("family", browserFamily.toString());
      final WebBrowserSettings value = myBrowserToSettingsMap.get(browserFamily);
      browser.setAttribute("path", value.getPath());
      browser.setAttribute("active", Boolean.toString(value.isActive()));
      final BrowserSpecificSettings specificSettings = value.getBrowserSpecificSettings();
      if (specificSettings != null) {
        final Element settingsElement = new Element("settings");
        XmlSerializer.serializeInto(specificSettings, settingsElement, new SkipDefaultValuesSerializationFilters());
        browser.addContent(settingsElement);
      }
      element.addContent(browser);
    }

    return element;
  }

  @SuppressWarnings({"unchecked"})
  public void loadState(@NonNls Element element) {
    for (@NonNls Element child : (Iterable<? extends Element>)element.getChildren("browser")) {
      String family = child.getAttributeValue("family");
      final String path = child.getAttributeValue("path");
      final String active = child.getAttributeValue("active");
      final BrowserFamily browserFamily;
      Element settingsElement = child.getChild("settings");

      try {
        browserFamily = BrowserFamily.valueOf(family);
        BrowserSpecificSettings specificSettings = null;
        if (settingsElement != null) {
          specificSettings = browserFamily.createBrowserSpecificSettings();
          XmlSerializer.deserializeInto(specificSettings, settingsElement);
        }
        myBrowserToSettingsMap.put(browserFamily, new WebBrowserSettings(path, Boolean.parseBoolean(active), specificSettings));
      }
      catch (IllegalArgumentException e) {
        // skip
      }
    }
  }

  public List<BrowserFamily> getActiveBrowsers() {
    final List<BrowserFamily> browsers = new ArrayList<BrowserFamily>();
    for (BrowserFamily family : BrowserFamily.values()) {
      if (getBrowserSettings(family).isActive()) {
        browsers.add(family);
      }
    }
    return browsers;
  }

  public void updateBrowserValue(final BrowserFamily family, final String path, boolean isActive) {
    final WebBrowserSettings settings = getBrowserSettings(family);
    myBrowserToSettingsMap.put(family, new WebBrowserSettings(path, isActive, settings.getBrowserSpecificSettings()));
  }

  public void updateBrowserSpecificSettings(BrowserFamily family, BrowserSpecificSettings specificSettings) {
    final WebBrowserSettings settings = getBrowserSettings(family);
    myBrowserToSettingsMap.put(family, new WebBrowserSettings(settings.getPath(), settings.isActive(), specificSettings));
  }

  @NotNull
  public WebBrowserSettings getBrowserSettings(@NotNull final BrowserFamily browserFamily) {
    WebBrowserSettings result = myBrowserToSettingsMap.get(browserFamily);
    if (result == null) {
      final String path = browserFamily.getExecutionPath();
      result = new WebBrowserSettings(path == null ? "" : path, path != null, null);
      myBrowserToSettingsMap.put(browserFamily, result);
    }

    return result;
  }

  public static BrowsersConfiguration getInstance() {
    return ServiceManager.getService(BrowsersConfiguration.class);
  }

  public static void launchBrowser(final @Nullable BrowserFamily family, @NotNull final String url) {
    if (family == null) {
      BrowserUtil.launchBrowser(url);
    }
    else {
      getInstance().doLaunchBrowser(family, url, ArrayUtil.EMPTY_STRING_ARRAY, Conditions.<String>alwaysTrue(), false);
    }
  }

  @Nullable
  private static String getWindowsPathToChrome() {
    if (!SystemInfo.isWindows) return null;

    String localSettings = SystemProperties.getUserHome() + (SystemInfo.isWindows7 ? "/AppData/Local" : "/Local Settings");
    return FileUtil.toSystemDependentName(localSettings + "/Google/Chrome/Application/chrome.exe");
  }

  public static void launchBrowser(final @NotNull BrowserFamily family, @NotNull final String url, String... parameters) {
    launchBrowser(family, url, false, parameters);
  }

  public static void launchBrowser(final @NotNull BrowserFamily family,
                                   @NotNull final String url,
                                   final boolean forceOpenNewInstanceOnMac,
                                   String... parameters) {
    launchBrowser(family, url, forceOpenNewInstanceOnMac, Conditions.<String>alwaysTrue(), parameters);
  }

  public static void launchBrowser(final @NotNull BrowserFamily family,
                                   @NotNull final String url,
                                   final boolean forceOpenNewInstanceOnMac,
                                   final Condition<String> browserSpecificParametersFilter,
                                   String... parameters) {
    getInstance().doLaunchBrowser(family, url, parameters, browserSpecificParametersFilter, forceOpenNewInstanceOnMac);
  }

  private void doLaunchBrowser(final BrowserFamily family,
                               @NotNull String url,
                               @NotNull String[] additionalParameters,
                               @NotNull Condition<String> browserSpecificParametersFilter,
                               final boolean forceOpenNewInstanceOnMac) {
    final WebBrowserSettings settings = getBrowserSettings(family);
    final String path = settings.getPath();
    if (path != null && path.length() > 0) {
      url = BrowserUtil.escapeUrl(url);
      try {
        final BrowserSpecificSettings specificSettings = settings.getBrowserSpecificSettings();
        final String[] browserParameters = specificSettings != null ? specificSettings.getAdditionalParameters() : ArrayUtil.EMPTY_STRING_ARRAY;
        String[] parameters = ArrayUtil.mergeArrays(ContainerUtil.findAllAsArray(browserParameters, browserSpecificParametersFilter),
                                                    additionalParameters);
        launchBrowser(path, url, forceOpenNewInstanceOnMac, parameters);
      }
      catch (IOException e) {
        Messages.showErrorDialog(e.getMessage(), XmlBundle.message("browser.error"));
      }
    }
    else {
      Messages.showErrorDialog(XmlBundle.message("browser.path.not.specified", family.getName()),
                               XmlBundle.message("browser.path.not.specified.title"));
    }
  }

  /**
   * @deprecated use {@link #launchBrowser(com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily, String)} instead
   */
  public static void launchBrowser(@NonNls @NotNull String browserPath, @NonNls String... parameters) throws IOException {
    launchBrowser(browserPath, parameters[parameters.length - 1], false, Arrays.copyOf(parameters, parameters.length-1));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void launchBrowser(@NonNls @NotNull String browserPath,
                                    String url, final boolean forceOpenNewInstanceOnMac,
                                    @NonNls String... browserArgs) throws IOException {
    final String[] command = BrowserUtil.getOpenBrowserCommand(browserPath);
    String[] args = ArrayUtil.append(browserArgs, url);

    if (SystemInfo.isMac && ExecUtil.getOpenCommandPath().equals(command[0])) {
      if (browserArgs.length > 0) {
        if (BrowserUtil.isOpenCommandSupportArgs()) {
          args = ArrayUtil.mergeArrays(new String[]{url, "--args"}, browserArgs);
        }
        else {
          args = new String[]{url};
          LOG.warn("'open' command doesn't allow to pass command line arguments so they will be ignored: " + Arrays.toString(args));
        }
      }
      if (forceOpenNewInstanceOnMac) {
        args = ArrayUtil.mergeArrays(new String[]{"-n"}, args);
      }
    }

    final String[] commandLine = ArrayUtil.mergeArrays(command, args);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Launching browser: " + Arrays.toString(commandLine));
    }
    Runtime.getRuntime().exec(commandLine);
  }

  @Nullable
  public static BrowserFamily findFamilyByName(@Nullable String name) {
    for (BrowserFamily family : BrowserFamily.values()) {
      if (family.getName().equals(name)) {
        return family;
      }
    }
    return null;
  }
}
