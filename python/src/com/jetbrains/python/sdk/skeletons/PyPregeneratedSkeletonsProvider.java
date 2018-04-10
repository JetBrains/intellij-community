/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.skeletons;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.Sdk;


public interface PyPregeneratedSkeletonsProvider {
  ExtensionPointName<PyPregeneratedSkeletonsProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyPregeneratedSkeletonsProvider");

  static PyPregeneratedSkeletons findPregeneratedSkeletonsForSdk(Sdk sdk, int generatorVersion) {
    for (PyPregeneratedSkeletonsProvider provider : Extensions.getExtensions(EP_NAME)) {
      PyPregeneratedSkeletons skeletons = provider.getSkeletonsForSdk(sdk, generatorVersion);
      if (skeletons != null) {
        return skeletons;
      }
    }
    return null;
  }

  PyPregeneratedSkeletons getSkeletonsForSdk(Sdk sdk, int generatorVersion);
}
