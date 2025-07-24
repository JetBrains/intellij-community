// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


/**
 * Various ancient utils that are still used, but will be dropped soon.
 *
 * @deprecated Use {@link com.jetbrains.python.newProjectWizard}
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public final class DeprecatedUtils {
  private DeprecatedUtils() {
  }

  private static @NotNull PyPackageManagementService.PyPackageInstallationErrorDescription getErrorDescription(final @Nullable Pair<Sdk, ExecutionException> sdkAndException,
                                                                                                               @NotNull String packageName) {
    PyPackageManagementService.PyPackageInstallationErrorDescription errorDescription = null;
    if (sdkAndException != null) {
      final ExecutionException exception = sdkAndException.second;
      errorDescription =
        PyPackageManagementService.toErrorDescription(Collections.singletonList(exception), sdkAndException.first, packageName);
      if (errorDescription == null) {
        errorDescription = PyPackageManagementService.PyPackageInstallationErrorDescription.createFromMessage(exception.getMessage());
      }
    }

    if (errorDescription == null) {
      errorDescription = PyPackageManagementService.PyPackageInstallationErrorDescription.createFromMessage(
        PyBundle.message("python.new.project.error.solution.another.sdk"));
    }
    return errorDescription;
  }


  //TODO: Support for plugin also

  // TODO: Migrate to interpreter service
  public static @NotNull List<Sdk> getValidPythonSdks(@NotNull List<Sdk> existingSdks) {
    return StreamEx
      .of(existingSdks)
      .filter(sdk -> sdk != null && sdk.getSdkType() instanceof PythonSdkType && PySdkExtKt.getSdkSeemsValid(sdk))
      .sorted(new PreferredSdkComparator())
      .toList();
  }
}
