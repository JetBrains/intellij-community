// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.featuresTrainer.ift;

import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;


/**
 * @deprecated Various ancient utils that are still used, but will be dropped soon.
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
final class DeprecatedUtils {
  private DeprecatedUtils() {
  }


  // TODO: Migrate to interpreter service
  static @NotNull List<@NotNull Sdk> getValidPythonSdks(@NotNull List<@NotNull Sdk> existingSdks) {
    return StreamEx
      .of(existingSdks)
      .filter(sdk -> sdk.getSdkType() instanceof PythonSdkType && PySdkExtKt.getSdkSeemsValid(sdk))
      .sorted(new PreferredSdkComparator())
      .toList();
  }
}
