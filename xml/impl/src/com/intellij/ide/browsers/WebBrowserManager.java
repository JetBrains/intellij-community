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

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily;

@State(name = "WebBrowsersConfiguration", storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/browsers.xml")})
public class WebBrowserManager implements PersistentStateComponent<Element> {
  final Map<BrowserFamily, WebBrowserSettings> myBrowserToSettingsMap = new LinkedHashMap<BrowserFamily, WebBrowserSettings>();

  public static WebBrowserManager getInstance() {
    return ServiceManager.getService(WebBrowserManager.class);
  }

  @Override
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

  @NotNull
  public List<WebBrowser> getBrowsers() {
    List<WebBrowser> result = new ArrayList<WebBrowser>();
    for (BrowserFamily family : BrowserFamily.values()) {
      result.add(WebBrowser.getStandardBrowser(family));
    }
    return result;
  }

  @NotNull
  public List<WebBrowser> getActiveBrowsers() {
    List<WebBrowser> result = new SmartList<WebBrowser>();
    for (WebBrowser browser : getBrowsers()) {
      if (getBrowserSettings(browser).isActive()) {
        result.add(browser);
      }
    }
    return result;
  }

  @NotNull
  public WebBrowserSettings getBrowserSettings(@NotNull WebBrowser browser) {
    return getBrowserSettings(browser.getFamily());
  }

  @NotNull
  public WebBrowserSettings getBrowserSettings(@NotNull BrowserFamily family) {
    WebBrowserSettings result = myBrowserToSettingsMap.get(family);
    if (result == null) {
      String path = family.getExecutionPath();
      result = new WebBrowserSettings(StringUtil.notNullize(path), path != null, null);
      myBrowserToSettingsMap.put(family, result);
    }
    return result;
  }

  public void updateBrowserSpecificSettings(@NotNull WebBrowser browser, BrowserSpecificSettings specificSettings) {
    updateBrowserSpecificSettings(browser.getFamily(), specificSettings);
  }

  public void updateBrowserSpecificSettings(@NotNull BrowserFamily family, BrowserSpecificSettings specificSettings) {
    WebBrowserSettings settings = getBrowserSettings(family);
    myBrowserToSettingsMap.put(family, new WebBrowserSettings(settings.getPath(), settings.isActive(), specificSettings));
  }

  public void updateBrowserValue(@NotNull WebBrowser browser, String path, boolean isActive) {
    WebBrowserSettings settings = getBrowserSettings(browser);
    myBrowserToSettingsMap.put(browser.getFamily(), new WebBrowserSettings(path, isActive, settings.getBrowserSpecificSettings()));
  }

  @Nullable
  public WebBrowser findBrowserByName(@Nullable String name) {
    for (BrowserFamily family : BrowserFamily.values()) {
      if (family.getName().equals(name)) {
        return WebBrowser.getStandardBrowser(family);
      }
    }
    return null;
  }

  public void updateBrowserValue(BrowserFamily family, String path, boolean isActive) {
    myBrowserToSettingsMap.put(family, new WebBrowserSettings(path, isActive, getBrowserSettings(family).getBrowserSpecificSettings()));
  }
}