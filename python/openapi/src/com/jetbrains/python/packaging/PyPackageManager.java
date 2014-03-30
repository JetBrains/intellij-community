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
package com.jetbrains.python.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;

import java.awt.*;

/**
 * @author yole
 */
public abstract class PyPackageManager {
  public static PyPackageManager getInstance(Sdk sdk) {
    return PyPackageManagers.getInstance().forSdk(sdk);
  }

  /**
   * Returns true if pip is installed for the specific interpreter; returns false if pip is not
   * installed or if it is not currently known whether it's installed (e.g. for a remote interpreter).
   *
   * @return true if pip is known to be installed, false otherwise.
   */
  public abstract boolean hasPip();
  public abstract void install(String requirementString) throws PyExternalProcessException;
  public abstract void showInstallationError(Project project, String title, String description);
  public abstract void showInstallationError(Component owner, String title, String description);
  public abstract void refresh();
}
