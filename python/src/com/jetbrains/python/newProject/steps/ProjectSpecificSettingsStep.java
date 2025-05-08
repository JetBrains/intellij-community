// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.steps;

import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated Use {@link com.jetbrains.python.newProjectWizard}
 */
@Deprecated(forRemoval = true)
public final class ProjectSpecificSettingsStep {
  private ProjectSpecificSettingsStep() {
  }

  // TODO: Migrate to interpreter service
  public static @NotNull List<Sdk> getValidPythonSdks(@NotNull List<Sdk> existingSdks) {
    return StreamEx
      .of(existingSdks)
      .filter(sdk -> sdk != null && sdk.getSdkType() instanceof PythonSdkType && PySdkExtKt.getSdkSeemsValid(sdk))
      .sorted(new PreferredSdkComparator())
      .toList();
  }
}
