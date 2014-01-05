package com.intellij.ide.browsers;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.UUID;

final class ConfigurableWebBrowser extends WebBrowser {
  private boolean active;
  private final UUID id;
  private String path;

  private BrowserSpecificSettings specificSettings;

  public ConfigurableWebBrowser(@NotNull UUID id, @NotNull BrowsersConfiguration.BrowserFamily family) {
    this(id, family, family.getName(), family.getExecutionPath(), true, family.createBrowserSpecificSettings());
  }

  public ConfigurableWebBrowser(@NotNull UUID id,
                                @NotNull BrowsersConfiguration.BrowserFamily family,
                                @NotNull String name,
                                @Nullable String path,
                                boolean active,
                                @Nullable BrowserSpecificSettings specificSettings) {
    super(family, name);

    this.id = id;
    this.path = path;
    this.active = active;
    this.specificSettings = specificSettings;
  }

  public void setName(@NotNull String value) {
    name = value;
  }

  public void setFamily(@NotNull BrowsersConfiguration.BrowserFamily value) {
    family = value;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return family.getIcon();
  }

  @Nullable
  @Override
  public String getPath() {
    return path;
  }

  public void setPath(@Nullable String value) {
    path = value;
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
  @NotNull
  public UUID getId() {
    return id;
  }

  public boolean isChanged(@NotNull ConfigurableWebBrowser info) {
    return active != info.active || family != info.family || !StringUtil.equals(name, info.name) || !StringUtil.equals(path, info.path);
  }
}