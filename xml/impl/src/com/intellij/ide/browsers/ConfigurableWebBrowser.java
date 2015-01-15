package com.intellij.ide.browsers;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.UUID;

final class ConfigurableWebBrowser extends WebBrowser {
  private final UUID id;
  @NotNull private BrowserFamily family;
  @NotNull private String name;
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
    this.id = id;
    this.family = family;
    this.name = name;

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
      if (WebBrowserManager.isYandexBrowser(this)) {
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
      else if (checkNameAndPath("node-webkit") || checkNameAndPath("nw") || checkNameAndPath("nwjs")) {
        return AllIcons.Xml.Browsers.Nwjs16;
      }
    }
    return family.getIcon();
  }

  private boolean checkNameAndPath(@NotNull String what) {
    return WebBrowserManager.checkNameAndPath(what, this);
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

  @Override
  @NotNull
  public String getName() {
    return name;
  }

  @Override
  @NotNull
  public final UUID getId() {
    return id;
  }

  @Override
  @NotNull
  public BrowserFamily getFamily() {
    return family;
  }

  @Override
  @NotNull
  public String getBrowserNotFoundMessage() {
    return IdeBundle.message("error.0.browser.path.not.specified", getName());
  }

  @Override
  public String toString() {
    return getName() + " (" + getPath() + ")";
  }
}