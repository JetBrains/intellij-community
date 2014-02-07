/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.NullableComputable;
import com.intellij.util.xmlb.Converter;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.UUID;

import static com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily;

public abstract class WebBrowser {
  protected @NotNull BrowserFamily family;
  protected @NotNull String name;
  private final UUID id;

  protected WebBrowser(@NotNull UUID id, @NotNull BrowserFamily family, @NotNull String name) {
    this.id = id;
    this.family = family;
    this.name = name;
  }

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public final UUID getId() {
    return id;
  }

  @NotNull
  public BrowserFamily getFamily() {
    return family;
  }

  @NotNull
  public abstract Icon getIcon();

  @Nullable
  public abstract String getPath();

  @NotNull
  public String getBrowserNotFoundMessage() {
    return XmlBundle.message("error.0.browser.path.not.specified", getName());
  }

  @Nullable
  public BrowserSpecificSettings getSpecificSettings() {
    return null;
  }

  @NotNull
  public static WebBrowser createCustomBrowser(@NotNull BrowserFamily family,
                                               @NotNull String name,
                                               @NotNull UUID id,
                                               @NotNull Icon icon,
                                               @NotNull NullableComputable<String> pathComputable,
                                               @Nullable String browserNotFoundMessage) {
    return new CustomWebBrowser(id, family, name, icon, pathComputable, browserNotFoundMessage);
  }

  @Override
  public String toString() {
    return getName() + " (" + getPath() + ")";
  }

  public static final class ReferenceConverter extends Converter<WebBrowser> {
    @Nullable
    @Override
    public WebBrowser fromString(@NotNull String value) {
      return WebBrowserManager.getInstance().findBrowserById(value);
    }

    @NotNull
    @Override
    public String toString(@NotNull WebBrowser browser) {
      return browser.getId().toString();
    }
  }
}