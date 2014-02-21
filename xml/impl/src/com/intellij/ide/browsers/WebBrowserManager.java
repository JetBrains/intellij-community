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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@State(name = "WebBrowsersConfiguration", storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/web-browsers.xml")})
public class WebBrowserManager implements PersistentStateComponent<Element>, ModificationTracker {
  private static final Logger LOG = Logger.getInstance(WebBrowserManager.class);

  // default standard browser ID must be constant across all IDE versions on all machines for all users
  private static final UUID DEFAULT_CHROME_ID = UUID.fromString("98CA6316-2F89-46D9-A9E5-FA9E2B0625B3");
  // public, but only internal use
  public static final UUID DEFAULT_FIREFOX_ID = UUID.fromString("A7BB68E0-33C0-4D6F-A81A-AAC1FDB870C8");
  private static final UUID DEFAULT_SAFARI_ID = UUID.fromString("E5120D43-2C3F-47EF-9F26-65E539E05186");
  private static final UUID DEFAULT_OPERA_ID = UUID.fromString("53E2F627-B1A7-4DFA-BFA7-5B83CC034776");
  private static final UUID DEFAULT_EXPLORER_ID = UUID.fromString("16BF23D4-93E0-4FFC-BFD6-CB13575177B0");

  private List<ConfigurableWebBrowser> browsers;

  private long modificationCount;

  DefaultBrowser defaultBrowser = DefaultBrowser.SYSTEM;

  public WebBrowserManager() {
    browsers = new ArrayList<ConfigurableWebBrowser>();
    browsers.add(new ConfigurableWebBrowser(DEFAULT_CHROME_ID, BrowserFamily.CHROME));
    browsers.add(new ConfigurableWebBrowser(DEFAULT_FIREFOX_ID, BrowserFamily.FIREFOX));
    browsers.add(new ConfigurableWebBrowser(DEFAULT_SAFARI_ID, BrowserFamily.SAFARI));
    browsers.add(new ConfigurableWebBrowser(DEFAULT_OPERA_ID, BrowserFamily.OPERA));
    browsers.add(new ConfigurableWebBrowser(DEFAULT_EXPLORER_ID, BrowserFamily.EXPLORER));
  }

  public static WebBrowserManager getInstance() {
    return ServiceManager.getService(WebBrowserManager.class);
  }

  boolean isPredefinedBrowser(@NotNull ConfigurableWebBrowser browser) {
    UUID id = browser.getId();
    return id.equals(DEFAULT_CHROME_ID) ||
           id.equals(DEFAULT_FIREFOX_ID) ||
           id.equals(DEFAULT_SAFARI_ID) ||
           id.equals(DEFAULT_OPERA_ID) ||
           id.equals(DEFAULT_EXPLORER_ID);
  }

  public enum DefaultBrowser {
    SYSTEM, FIRST, ALTERNATIVE
  }

  @NotNull
  public DefaultBrowser getDefaultBrowserMode() {
    return defaultBrowser;
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    if (defaultBrowser != DefaultBrowser.SYSTEM) {
      state.setAttribute("default", defaultBrowser.name().toLowerCase());
    }

    for (ConfigurableWebBrowser browser : browsers) {
      Element entry = new Element("browser");
      entry.setAttribute("id", browser.getId().toString());
      entry.setAttribute("name", browser.getName());
      entry.setAttribute("family", browser.getFamily().name());

      String path = browser.getPath();
      if (path != null && !path.equals(browser.getFamily().getExecutionPath())) {
        entry.setAttribute("path", path);
      }

      if (!browser.isActive()) {
        entry.setAttribute("active", "false");
      }

      BrowserSpecificSettings specificSettings = browser.getSpecificSettings();
      if (specificSettings != null) {
        Element settingsElement = new Element("settings");
        XmlSerializer.serializeInto(specificSettings, settingsElement, new SkipDefaultValuesSerializationFilters());
        if (!JDOMUtil.isEmpty(settingsElement)) {
          entry.addContent(settingsElement);
        }
      }
      state.addContent(entry);
    }
    return state;
  }

  @Nullable
  private static BrowserFamily readFamily(String value) {
    try {
      return BrowserFamily.valueOf(value);
    }
    catch (RuntimeException e) {
      LOG.warn(e);

      for (BrowserFamily family : BrowserFamily.values()) {
        if (family.getName().equalsIgnoreCase(value)) {
          return family;
        }
      }

      return null;
    }
  }

  @Nullable
  private static UUID readId(String value, @NotNull BrowserFamily family, @NotNull List<ConfigurableWebBrowser> existingBrowsers) {
    if (StringUtil.isEmpty(value)) {
      UUID id;
      switch (family) {
        case CHROME:
          id = DEFAULT_CHROME_ID;
          break;
        case EXPLORER:
          id = DEFAULT_EXPLORER_ID;
          break;
        case FIREFOX:
          id = DEFAULT_FIREFOX_ID;
          break;
        case OPERA:
          id = DEFAULT_OPERA_ID;
          break;
        case SAFARI:
          id = DEFAULT_SAFARI_ID;
          break;

        default:
          return null;
      }

      for (ConfigurableWebBrowser browser : existingBrowsers) {
        if (browser.getId() == id) {
          // duplicated entry, skip
          return null;
        }
      }
      return id;
    }
    else {
      try {
        return UUID.fromString(value);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    return null;
  }

  @Override
  public void loadState(Element element) {
    String defaultValue = element.getAttributeValue("default");
    if (!StringUtil.isEmpty(defaultValue)) {
      try {
        defaultBrowser = DefaultBrowser.valueOf(defaultValue.toUpperCase());
      }
      catch (IllegalArgumentException e) {
        LOG.warn(e);
      }
    }

    List<ConfigurableWebBrowser> list = new ArrayList<ConfigurableWebBrowser>();
    for (Element child : element.getChildren("browser")) {
      BrowserFamily family = readFamily(child.getAttributeValue("family"));
      if (family == null) {
        continue;
      }

      UUID id = readId(child.getAttributeValue("id"), family, list);
      if (id == null) {
        continue;
      }

      Element settingsElement = child.getChild("settings");
      BrowserSpecificSettings specificSettings = family.createBrowserSpecificSettings();
      if (specificSettings != null && settingsElement != null) {
        try {
          XmlSerializer.deserializeInto(specificSettings, settingsElement);
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      }

      String activeValue = child.getAttributeValue("active");

      String path = StringUtil.nullize(child.getAttributeValue("path"), true);
      if (path == null) {
        path = family.getExecutionPath();
      }

      list.add(new ConfigurableWebBrowser(id,
                                          family,
                                          StringUtil.notNullize(child.getAttributeValue("name"), family.getName()),
                                          path,
                                          activeValue == null || Boolean.parseBoolean(activeValue),
                                          specificSettings));
    }

    setList(list);
  }

  @NotNull
  public List<WebBrowser> getBrowsers() {
    return Collections.<WebBrowser>unmodifiableList(browsers);
  }

  @NotNull
  List<ConfigurableWebBrowser> getList() {
    return browsers;
  }

  void setList(@NotNull List<ConfigurableWebBrowser> value) {
    browsers = value;
    modificationCount++;
  }

  @NotNull
  public List<WebBrowser> getActiveBrowsers() {
    return getBrowsers(Conditions.<WebBrowser>alwaysTrue(), true);
  }

  @NotNull
  public List<WebBrowser> getBrowsers(@NotNull Condition<WebBrowser> condition) {
    return getBrowsers(condition, true);
  }

  @NotNull
  public List<WebBrowser> getBrowsers(@NotNull Condition<WebBrowser> condition, boolean onlyActive) {
    List<WebBrowser> result = new SmartList<WebBrowser>();
    for (ConfigurableWebBrowser browser : browsers) {
      if ((!onlyActive || browser.isActive()) && condition.value(browser)) {
        result.add(browser);
      }
    }
    return result;
  }

  public void setBrowserSpecificSettings(@NotNull WebBrowser browser, @NotNull BrowserSpecificSettings specificSettings) {
    ((ConfigurableWebBrowser)browser).setSpecificSettings(specificSettings);
  }

  public void setBrowserPath(@NotNull WebBrowser browser, @Nullable String path, boolean isActive) {
    ((ConfigurableWebBrowser)browser).setPath(path);
    ((ConfigurableWebBrowser)browser).setActive(isActive);
  }

  public WebBrowser addBrowser(final @NotNull UUID id,
                               final @NotNull BrowserFamily family,
                               final @NotNull String name,
                               final @Nullable String path,
                               final boolean active,
                               final BrowserSpecificSettings specificSettings) {
    final ConfigurableWebBrowser browser = new ConfigurableWebBrowser(id, family, name, path, active, specificSettings);
    browsers.add(browser);
    modificationCount++;
    return browser;
  }

  @Nullable
  private static UUID parseUuid(@NotNull String id) {
    if (id.indexOf('-') == -1) {
      return null;
    }

    try {
      return UUID.fromString(id);
    }
    catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  @Nullable
  /**
   * @param idOrFamilyName UUID or, due to backward compatibility, browser family name or JS debugger engine ID
   */
  public WebBrowser findBrowserById(@Nullable String idOrFamilyName) {
    if (StringUtil.isEmpty(idOrFamilyName)) {
      return null;
    }

    UUID id = parseUuid(idOrFamilyName);
    if (id == null) {
      for (ConfigurableWebBrowser browser : browsers) {
        if (browser.getFamily().name().equalsIgnoreCase(idOrFamilyName) ||
            browser.getFamily().getName().equalsIgnoreCase(idOrFamilyName)) {
          return browser;
        }
      }
      return null;
    }

    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.getId().equals(id)) {
        return browser;
      }
    }
    return null;
  }

  @NotNull
  @Deprecated
  /**
   * @deprecated Use {@link #getFirstBrowser(BrowserFamily)}
   */
  public WebBrowser getBrowser(@NotNull BrowserFamily family) {
    return getFirstBrowser(family);
  }

  @NotNull
  public WebBrowser getFirstBrowser(@NotNull BrowserFamily family) {
    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.isActive() && family.equals(browser.getFamily())) {
        return browser;
      }
    }

    for (ConfigurableWebBrowser browser : browsers) {
      if (family.equals(browser.getFamily())) {
        return browser;
      }
    }

    throw new IllegalStateException("Must be at least one browser per family");
  }

  public boolean isActive(@NotNull WebBrowser browser) {
    return !(browser instanceof ConfigurableWebBrowser) || ((ConfigurableWebBrowser)browser).isActive();
  }

  @Nullable
  public WebBrowser getDefaultBrowser() {
    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.isActive()) {
        return browser;
      }
    }
    return null;
  }

  @Override
  public long getModificationCount() {
    return modificationCount;
  }
}