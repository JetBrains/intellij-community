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
package com.intellij.ide.browsers;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.firefox.FirefoxSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
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
import java.util.Map;

/**
 * @author spleaner
 */
@State(name = "WebBrowsersConfiguration", storages = {@Storage(id = "other", file = "$APP_CONFIG$/browsers.xml")})
public class BrowsersConfiguration implements PersistentStateComponent<Element> {
  public static enum BrowserFamily {
    EXPLORER(XmlBundle.message("browsers.explorer"), "iexplore", null, null, IconLoader.getIcon("/xml/browsers/explorer16.png")),
    SAFARI(XmlBundle.message("browsers.safari"), "safari", "safari", "Safari", IconLoader.getIcon("/xml/browsers/safari16.png")),
    OPERA(XmlBundle.message("browsers.opera"), "opera", "opera", "Opera", IconLoader.getIcon("/xml/browsers/opera16.png")),
    FIREFOX(XmlBundle.message("browsers.firefox"), "firefox", "firefox", "Firefox", IconLoader.getIcon("/xml/browsers/firefox16.png")) {
      @Override
      public BrowserSpecificSettings createBrowserSpecificSettings() {
        return new FirefoxSettings();
      }
    },
    CHROME(XmlBundle.message("browsers.chrome"), "chrome", null, null, IconLoader.getIcon("/xml/browsers/chrome16.png"));

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

  public static void launchBrowser(final BrowserFamily family, @NotNull final String url) {
    getInstance()._launchBrowser(family, url);
  }

  private void _launchBrowser(final BrowserFamily family, @NotNull String url) {
    final WebBrowserSettings settings = getBrowserSettings(family);
    final String path = settings.getPath();
    if (path != null && path.length() > 0) {
      url = BrowserUtil.escapeUrl(url);
      try {
        final BrowserSpecificSettings specificSettings = settings.getBrowserSpecificSettings();
        String[] parameters;
        if (specificSettings != null) {
          parameters = ArrayUtil.append(specificSettings.getAdditionalParameters(), url);
        }
        else {
          parameters = new String[]{url};
        }
        launchBrowser(path, parameters);
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void launchBrowser(@NonNls @NotNull String browserPath, @NonNls String... parameters) throws IOException {
    final String[] command = BrowserUtil.getOpenBrowserCommand(browserPath, parameters);
    Runtime.getRuntime().exec(ArrayUtil.mergeArrays(command, parameters, String.class));
  }
}
