// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.python.sdk.backend.PythonInterpreter;

public final class PythonModulePathCache extends PythonPathCache {
  public static PythonPathCache getInstance(Module module) {
    return PythonModulePathCacheManager.getInstance(module.getProject()).getPythonPathCache(module);
  }

  public PythonModulePathCache(Module module) {
    updateCacheForSdk(module);
  }

  private static void updateCacheForSdk(Module module) {
    final PythonInterpreter interpreter = MainPythonSdkKt.pythonInterpreterIfReady(module);
    if (interpreter != null) {
      // initialize cache for the interpreter
      PythonSdkPathCache.getInstance(module.getProject(), interpreter);
    }
  }
}
