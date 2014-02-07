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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class BrowsersConfiguration {

  @SuppressWarnings({"UnusedDeclaration"})
  @Nullable
  @Deprecated
  /**
   * @deprecated  to remove in IDEA 14
   */
  public static BrowsersConfiguration getInstance() {
    return ServiceManager.getService(BrowsersConfiguration.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  @Nullable
  @Deprecated
  /**
   * @deprecated  to remove in IDEA 14
   */
  public BrowserFamily findFamilyByName(@Nullable String name) {
    for (BrowserFamily family : BrowserFamily.values()) {
      if (family.getName().equals(name)) {
        return family;
      }
    }
    return null;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  @Nullable
  @Deprecated
  /**
   * @deprecated  to remove in IDEA 14
   */
  public BrowserFamily findFamilyByPath(@Nullable String path) {
    if (!StringUtil.isEmptyOrSpaces(path)) {
      String name = FileUtil.getNameWithoutExtension(new File(path).getName());
      for (BrowserFamily family : BrowserFamily.values()) {
        if (name.equalsIgnoreCase(family.getExecutionPath())) {
          return family;
        }
      }
    }

    return null;
  }
}