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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NullableComputable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.UUID;

public abstract class WebBrowserBase extends WebBrowser {
  protected @NotNull BrowserFamily family;
  protected @NotNull String name;
  private final UUID id;

  protected WebBrowserBase(@NotNull UUID id, @NotNull BrowserFamily family, @NotNull String name) {
    this.id = id;
    this.family = family;
    this.name = name;
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
}