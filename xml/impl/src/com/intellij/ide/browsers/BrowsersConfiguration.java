/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.browsers.chrome.ChromeSettings;
import com.intellij.ide.browsers.firefox.FirefoxSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xml.XmlBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@State(name = "WebBrowsersConfiguration", storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/browsers.xml")})
public class BrowsersConfiguration implements PersistentStateComponent<Element> {
  public enum BrowserFamily {
    CHROME(XmlBundle.message("browsers.chrome"), "chrome", "google-chrome", "Google Chrome", AllIcons.Xml.Browsers.Chrome16) {
      @Override
      public BrowserSpecificSettings createBrowserSpecificSettings() {
        return new ChromeSettings();
      }
    },
    EXPLORER(XmlBundle.message("browsers.explorer"), "iexplore", null, null, AllIcons.Xml.Browsers.Explorer16),
    FIREFOX(XmlBundle.message("browsers.firefox"), "firefox", "firefox", "Firefox", AllIcons.Xml.Browsers.Firefox16) {
      @Override
      public BrowserSpecificSettings createBrowserSpecificSettings() {
        return new FirefoxSettings();
      }
    },
    OPERA(XmlBundle.message("browsers.opera"), "opera", "opera", "Opera", AllIcons.Xml.Browsers.Opera16),
    SAFARI(XmlBundle.message("browsers.safari"), "safari", null, "Safari", AllIcons.Xml.Browsers.Safari16);

    private final String myName;
    private final String myWindowsPath;
    private final String myUnixPath;
    private final String myMacPath;
    private final Icon myIcon;

    BrowserFamily(final String name,
                  @NonNls final String windowsPath,
                  @NonNls final String unixPath,
                  @NonNls final String macPath,
                  final Icon icon) {
      myName = name;
      myWindowsPath = windowsPath;
      myUnixPath = unixPath;
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
      else if (SystemInfo.isMac) {
        return myMacPath;
      }
      else if (SystemInfo.isUnix) {
        return myUnixPath;
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

  private final Map<BrowserFamily, WebBrowserSettings> myBrowserToSettingsMap = new LinkedHashMap<BrowserFamily, WebBrowserSettings>();

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element getState() {
    @NonNls Element element = new Element("WebBrowsersConfiguration");
    for (Map.Entry<BrowserFamily, WebBrowserSettings> entry : myBrowserToSettingsMap.entrySet()) {
      Element browser = new Element("browser");
      browser.setAttribute("family", entry.getKey().toString());
      WebBrowserSettings settings = entry.getValue();
      browser.setAttribute("path", settings.getPath());
      browser.setAttribute("active", Boolean.toString(settings.isActive()));
      BrowserSpecificSettings specificSettings = settings.getBrowserSpecificSettings();
      if (specificSettings != null) {
        Element settingsElement = new Element("settings");
        XmlSerializer.serializeInto(specificSettings, settingsElement, new SkipDefaultValuesSerializationFilters());
        if (!settingsElement.getContent().isEmpty()) {
          browser.addContent(settingsElement);
        }
      }
      element.addContent(browser);
    }

    return element;
  }

  @Override
  public void loadState(Element element) {
    for (Element child : element.getChildren("browser")) {
      Element settingsElement = child.getChild("settings");
      try {
        BrowserFamily browserFamily = BrowserFamily.valueOf(child.getAttributeValue("family"));
        BrowserSpecificSettings specificSettings = settingsElement == null ? null : browserFamily.createBrowserSpecificSettings();
        if (specificSettings != null) {
          XmlSerializer.deserializeInto(specificSettings, settingsElement);
        }
        myBrowserToSettingsMap.put(browserFamily, new WebBrowserSettings(child.getAttributeValue("path"), Boolean.parseBoolean(child.getAttributeValue("active")), specificSettings));
      }
      catch (IllegalArgumentException ignored) {
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
      result = new WebBrowserSettings(StringUtil.notNullize(path), path != null, null);
      myBrowserToSettingsMap.put(browserFamily, result);
    }

    return result;
  }

  public static BrowsersConfiguration getInstance() {
    return ServiceManager.getService(BrowsersConfiguration.class);
  }

  @Nullable
  public BrowserFamily findFamilyByName(@Nullable String name) {
    for (BrowserFamily family : BrowserFamily.values()) {
      if (family.getName().equals(name)) {
        return family;
      }
    }
    return null;
  }

  @Nullable
  public BrowserFamily findFamilyByPath(@Nullable String path) {
    if (!StringUtil.isEmptyOrSpaces(path)) {
      String name = FileUtil.getNameWithoutExtension(new File(path).getName());
      for (BrowserFamily family : BrowserFamily.values()) {
        if (name.equalsIgnoreCase(family.getExecutionPath())) {
          return family;
        }
      }
    }

    return null;
  }
}