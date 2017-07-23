/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "WebBrowsersConfiguration", storages = @Storage("web-browsers.xml"))
public class WebBrowserManager extends SimpleModificationTracker implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(WebBrowserManager.class);

  // default standard browser ID must be constant across all IDE versions on all machines for all users
  private static final UUID PREDEFINED_CHROME_ID = UUID.fromString("98CA6316-2F89-46D9-A9E5-FA9E2B0625B3");
  // public, but only internal use
  public static final UUID PREDEFINED_FIREFOX_ID = UUID.fromString("A7BB68E0-33C0-4D6F-A81A-AAC1FDB870C8");
  private static final UUID PREDEFINED_SAFARI_ID = UUID.fromString("E5120D43-2C3F-47EF-9F26-65E539E05186");
  private static final UUID PREDEFINED_OPERA_ID = UUID.fromString("53E2F627-B1A7-4DFA-BFA7-5B83CC034776");
  private static final UUID PREDEFINED_YANDEX_ID = UUID.fromString("B1B2EC2C-20BD-4EE2-89C4-616DB004BCD4");
  private static final UUID PREDEFINED_EXPLORER_ID = UUID.fromString("16BF23D4-93E0-4FFC-BFD6-CB13575177B0");

  private static final UUID[] PREDEFINED_BROWSER_IDS = new UUID[]{
    PREDEFINED_CHROME_ID,
    PREDEFINED_FIREFOX_ID,
    PREDEFINED_SAFARI_ID,
    PREDEFINED_OPERA_ID,
    PREDEFINED_YANDEX_ID,
    PREDEFINED_EXPLORER_ID
  };

  private static List<ConfigurableWebBrowser> getPredefinedBrowsers() {
    return Arrays.asList(
      new ConfigurableWebBrowser(PREDEFINED_CHROME_ID, BrowserFamily.CHROME),
      new ConfigurableWebBrowser(PREDEFINED_FIREFOX_ID, BrowserFamily.FIREFOX),
      new ConfigurableWebBrowser(PREDEFINED_SAFARI_ID, BrowserFamily.SAFARI),
      new ConfigurableWebBrowser(PREDEFINED_OPERA_ID, BrowserFamily.OPERA),
      new ConfigurableWebBrowser(PREDEFINED_YANDEX_ID, BrowserFamily.CHROME, "Yandex", SystemInfo.isWindows ? "browser" : (SystemInfo.isMac ? "Yandex" : "yandex"), false, BrowserFamily.CHROME.createBrowserSpecificSettings()),
      new ConfigurableWebBrowser(PREDEFINED_EXPLORER_ID, BrowserFamily.EXPLORER)
    );
  }

  private List<ConfigurableWebBrowser> browsers;
  private boolean myShowBrowserHover = true;
  DefaultBrowserPolicy defaultBrowserPolicy = DefaultBrowserPolicy.SYSTEM;

  public WebBrowserManager() {
    browsers = new ArrayList<>(getPredefinedBrowsers());
  }

  public static WebBrowserManager getInstance() {
    return ServiceManager.getService(WebBrowserManager.class);
  }

  public static boolean isYandexBrowser(@NotNull WebBrowser browser) {
    return browser.getFamily().equals(BrowserFamily.CHROME) && (browser.getId().equals(PREDEFINED_YANDEX_ID) || checkNameAndPath("Yandex", browser));
  }

  public static boolean isDartium(@NotNull WebBrowser browser) {
    return browser.getFamily().equals(BrowserFamily.CHROME) && checkNameAndPath("Dartium", browser);
  }

  static boolean checkNameAndPath(@NotNull String what, @NotNull WebBrowser browser) {
    if (StringUtil.containsIgnoreCase(browser.getName(), what)) {
      return true;
    }
    String path = browser.getPath();
    if (path != null) {
      int index = path.lastIndexOf('/');
      return index > 0 ? path.indexOf(what, index + 1) != -1 : path.contains(what);
    }
    return false;
  }

  boolean isPredefinedBrowser(@NotNull ConfigurableWebBrowser browser) {
    UUID id = browser.getId();
    for (UUID predefinedBrowserId : PREDEFINED_BROWSER_IDS) {
      if (id.equals(predefinedBrowserId)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public DefaultBrowserPolicy getDefaultBrowserPolicy() {
    return defaultBrowserPolicy;
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    if (defaultBrowserPolicy != DefaultBrowserPolicy.SYSTEM) {
      state.setAttribute("default", defaultBrowserPolicy.name().toLowerCase(Locale.ENGLISH));
    }
    if (!myShowBrowserHover) {
      state.setAttribute("showHover", "false");
    }

    if (!browsers.equals(getPredefinedBrowsers())) {
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
          id = PREDEFINED_CHROME_ID;
          break;
        case EXPLORER:
          id = PREDEFINED_EXPLORER_ID;
          break;
        case FIREFOX:
          id = PREDEFINED_FIREFOX_ID;
          break;
        case OPERA:
          id = PREDEFINED_OPERA_ID;
          break;
        case SAFARI:
          id = PREDEFINED_SAFARI_ID;
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
        defaultBrowserPolicy = DefaultBrowserPolicy.valueOf(defaultValue.toUpperCase(Locale.ENGLISH));
      }
      catch (IllegalArgumentException e) {
        LOG.warn(e);
      }
    }

    myShowBrowserHover = !"false".equals(element.getAttributeValue("showHover"));

    List<ConfigurableWebBrowser> list = new ArrayList<>();
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

    // add removed/new predefined browsers
    Map<UUID, ConfigurableWebBrowser> idToBrowser = null;
    int n = list.size();
    pb: for (UUID predefinedBrowserId : PREDEFINED_BROWSER_IDS) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < n; i++) {
        if (list.get(i).getId().equals(predefinedBrowserId)) {
          continue pb;
        }
      }

      if (idToBrowser == null) {
        idToBrowser = ContainerUtil.newMapFromValues(getPredefinedBrowsers().iterator(), it -> it.getId());
      }
      list.add(idToBrowser.get(predefinedBrowserId));
    }

    setList(list);
  }

  @NotNull
  public List<WebBrowser> getBrowsers() {
    return Collections.unmodifiableList(browsers);
  }

  @NotNull
  List<ConfigurableWebBrowser> getList() {
    return browsers;
  }

  void setList(@NotNull List<ConfigurableWebBrowser> value) {
    browsers = value;
    incModificationCount();
  }

  @NotNull
  public List<WebBrowser> getActiveBrowsers() {
    return getBrowsers(Conditions.alwaysTrue(), true);
  }

  @NotNull
  public List<WebBrowser> getBrowsers(@NotNull Condition<WebBrowser> condition) {
    return getBrowsers(condition, true);
  }

  @NotNull
  public List<WebBrowser> getBrowsers(@NotNull Condition<WebBrowser> condition, boolean onlyActive) {
    List<WebBrowser> result = new SmartList<>();
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
    incModificationCount();
  }

  public WebBrowser addBrowser(final @NotNull UUID id,
                               final @NotNull BrowserFamily family,
                               final @NotNull String name,
                               final @Nullable String path,
                               final boolean active,
                               final BrowserSpecificSettings specificSettings) {
    final ConfigurableWebBrowser browser = new ConfigurableWebBrowser(id, family, name, path, active, specificSettings);
    browsers.add(browser);
    incModificationCount();
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

  /**
   * @param idOrFamilyName UUID or, due to backward compatibility, browser family name or JS debugger engine ID
   */
  @Nullable
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

  @Nullable
  public WebBrowser getFirstBrowserOrNull(@NotNull BrowserFamily family) {
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

    return null;
  }

  @NotNull
  public WebBrowser getFirstBrowser(@NotNull BrowserFamily family) {
    WebBrowser result = getFirstBrowserOrNull(family);
    if (result == null) {
      throw new IllegalStateException("Must be at least one browser per family");
    }
    return result;
  }

  public boolean isActive(@NotNull WebBrowser browser) {
    return !(browser instanceof ConfigurableWebBrowser) || ((ConfigurableWebBrowser)browser).isActive();
  }

  @Nullable
  public WebBrowser getFirstActiveBrowser() {
    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.isActive() && browser.getPath() != null) {
        return browser;
      }
    }
    return null;
  }

  public void setShowBrowserHover(boolean showBrowserHover) {
    myShowBrowserHover = showBrowserHover;
  }

  public boolean isShowBrowserHover() {
    return myShowBrowserHover;
  }
}