// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface VFSTestFrameworkListener {
  void updateAllTestFrameworks(@NotNull Sdk sdk);

  boolean isTestFrameworkInstalled(@Nullable Sdk sdk, @NotNull String name);

  void setTestFrameworkInstalled(boolean installed, @NotNull String sdkHome, @NotNull String name);

  @NotNull
  static VFSTestFrameworkListener getInstance() {
    return ApplicationManager.getApplication().getService(VFSTestFrameworkListener.class);
  }
}
