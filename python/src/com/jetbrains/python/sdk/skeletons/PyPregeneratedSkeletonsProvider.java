// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.projectRoots.Sdk;


public interface PyPregeneratedSkeletonsProvider {
  ExtensionPointName<PyPregeneratedSkeletonsProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyPregeneratedSkeletonsProvider");

  static PyPregeneratedSkeletons findPregeneratedSkeletonsForSdk(Sdk sdk, int generatorVersion) {
    for (PyPregeneratedSkeletonsProvider provider : EP_NAME.getExtensionList()) {
      PyPregeneratedSkeletons skeletons = provider.getSkeletonsForSdk(sdk, generatorVersion);
      if (skeletons != null) {
        return skeletons;
      }
    }
    return null;
  }

  PyPregeneratedSkeletons getSkeletonsForSdk(Sdk sdk, int generatorVersion);
}
