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

import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class WebBrowserSettings {
  private final String myPath;
  private final boolean myActive;
  private final BrowserSpecificSettings myBrowserSpecificSettings;

  public WebBrowserSettings(String path, boolean active, BrowserSpecificSettings browserSpecificSettings) {
    myPath = path;
    myActive = active;
    myBrowserSpecificSettings = browserSpecificSettings;
  }

  public String getPath() {
    return myPath;
  }

  public boolean isActive() {
    return myActive;
  }

  @Nullable
  public BrowserSpecificSettings getBrowserSpecificSettings() {
    return myBrowserSpecificSettings;
  }
}
