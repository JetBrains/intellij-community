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
package com.intellij.ide.browsers.firefox;

import java.io.File;

/**
 * @author nik
 */
public class FirefoxProfile {
  private final String myName;
  private final String myPath;
  private final boolean myDefault;
  private final boolean myRelative;

  public FirefoxProfile(String name, String path, boolean aDefault, boolean relative) {
    myName = name;
    myPath = path;
    myDefault = aDefault;
    myRelative = relative;
  }

  public String getName() {
    return myName;
  }

  public String getPath() {
    return myPath;
  }

  public boolean isRelative() {
    return myRelative;
  }

  public boolean isDefault() {
    return myDefault;
  }

  public File getProfileDirectory(File profilesIniFile) {
    return myRelative ? new File(profilesIniFile.getParentFile(), myPath) : new File(myPath);
  }
}
