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
package com.intellij.ide.browsers.impl;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.UrlOpener;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DefaultUrlOpener extends UrlOpener {
  @Override
  public boolean openUrl(@NotNull WebBrowser browser, @NotNull String url, @Nullable Project project) {
    return BrowserLauncher.getInstance().browseUsingPath(url, null, browser, project, ArrayUtil.EMPTY_STRING_ARRAY);
  }
}