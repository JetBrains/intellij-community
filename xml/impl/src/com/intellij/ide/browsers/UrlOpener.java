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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class UrlOpener {
  public static final ExtensionPointName<UrlOpener> EP_NAME = ExtensionPointName.create("org.jetbrains.urlOpener");

  public static void launchBrowser(final @Nullable BrowsersConfiguration.BrowserFamily family, final @NotNull String url) {
    launchBrowser(url, family == null ? null : WebBrowser.getStandardBrowser(family));
  }

  // different params order in order not to break compilation for launchBrowser(null, url)
  public static void launchBrowser(final @NotNull String url, final @Nullable WebBrowser browser) {
    if (browser == null) {
      BrowserUtil.launchBrowser(url);
    }
    else {
      for (UrlOpener urlOpener : EP_NAME.getExtensions()) {
        if (urlOpener.openUrl(browser, url)) {
          return;
        }
      }
    }
  }

  public abstract boolean openUrl(final @NotNull WebBrowser browser, final @NotNull String url);
}
