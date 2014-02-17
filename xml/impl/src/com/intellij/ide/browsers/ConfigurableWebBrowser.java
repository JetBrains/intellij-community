package com.intellij.ide.browsers;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.UUID;

final class ConfigurableWebBrowser extends WebBrowserBase {
  private boolean active;
  private String path;

  private BrowserSpecificSettings specificSettings;

  @SuppressWarnings("UnusedDeclaration")
  public ConfigurableWebBrowser() {
    this(UUID.randomUUID(), BrowserFamily.CHROME);
  }

  public ConfigurableWebBrowser(@NotNull UUID id, @NotNull BrowserFamily family) {
    this(id, family, family.getName(), family.getExecutionPath(), true, family.createBrowserSpecificSettings());
  }

  public ConfigurableWebBrowser(@NotNull UUID id,
                                @NotNull BrowserFamily family,
                                @NotNull String name,
                                @Nullable String path,
                                boolean active,
                                @Nullable BrowserSpecificSettings specificSettings) {
    super(id, family, name);

    this.path = StringUtil.nullize(path);
    this.active = active;
    this.specificSettings = specificSettings;
  }

  public void setName(@NotNull String value) {
    name = value;
  }

  public void setFamily(@NotNull BrowserFamily value) {
    family = value;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    if (family == BrowserFamily.CHROME) {
      if (checkNameAndPath("Yandex")) {
        return AllIcons.Xml.Browsers.Yandex16;
      }
      else if (checkNameAndPath("Dartium") || checkNameAndPath("Chromium")) {
        return AllIcons.Xml.Browsers.Chromium16;
      }
      else if (checkNameAndPath("Canary")) {
        return AllIcons.Xml.Browsers.Canary16;
      }
      else if (checkNameAndPath("Opera")) {
        return AllIcons.Xml.Browsers.Opera16;
      }
    }
    return family.getIcon();
  }

  private boolean checkNameAndPath(@NotNull String what) {
    return StringUtil.containsIgnoreCase(name, what) || path != null && StringUtil.containsIgnoreCase(path, what);
  }

  @Nullable
  @Override
  public String getPath() {
    return path;
  }

  public void setPath(@Nullable String value) {
    path = PathUtil.toSystemIndependentName(StringUtil.nullize(value));
  }

  @Override
  @Nullable
  public BrowserSpecificSettings getSpecificSettings() {
    return specificSettings;
  }

  public void setSpecificSettings(@Nullable BrowserSpecificSettings value) {
    specificSettings = value;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConfigurableWebBrowser)) {
      return false;
    }

    ConfigurableWebBrowser browser = (ConfigurableWebBrowser)o;
    return getId().equals(browser.getId()) &&
           family.equals(browser.family) &&
           active == browser.active &&
           Comparing.strEqual(name, browser.name) &&
           Comparing.equal(path, browser.path) &&
           Comparing.equal(specificSettings, browser.specificSettings);
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }
}