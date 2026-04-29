// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;

public final class PythonModulePathCache extends PythonPathCache {
  public static PythonPathCache getInstance(Module module) {
    return PythonModulePathCacheManager.getInstance(module.getProject()).getPythonPathCache(module);
  }

  public PythonModulePathCache(Module module) {
    updateCacheForSdk(module);
  }

  private static void updateCacheForSdk(Module module) {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk != null) {
      // initialize cache for SDK
      PythonSdkPathCache.getInstance(module.getProject(), sdk);
    }
  }
}
