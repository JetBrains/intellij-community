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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily;

public class WebBrowserSettings {
  protected BrowserFamily family;
  protected String name;
  protected String path;
  protected boolean active;
  protected BrowserSpecificSettings specificSettings;

  public WebBrowserSettings(@NotNull BrowserFamily family, @NotNull String name, @NotNull String path, boolean active, @Nullable BrowserSpecificSettings specificSettings) {
    this.family = family;
    this.name = name;
    this.path = path;
    this.active = active;
    this.specificSettings = specificSettings;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  public boolean isActive() {
    return active;
  }

  @Nullable
  public BrowserSpecificSettings getSpecificSettings() {
    return specificSettings;
  }

  public MutableWebBrowserSettings createMutable() {
    return new MutableWebBrowserSettings(this);
  }

  public String getName() {
    return name;
  }

  public static class MutableWebBrowserSettings extends WebBrowserSettings {
    private MutableWebBrowserSettings(@NotNull WebBrowserSettings settings) {
      super(settings.family, settings.name, settings.path, settings.active, settings.specificSettings);
    }

    public void setActive(boolean value) {
      active = value;
    }

    public void setName(@NotNull String value) {
      name = value;
    }

    public void setPath(@NotNull String value) {
      path = value;
    }

    public boolean isChanged(@NotNull WebBrowserSettings info) {
      return active != info.active || family != info.family || !StringUtil.equals(name, info.name) || !StringUtil.equals(path, info.path);
    }
  }
}
