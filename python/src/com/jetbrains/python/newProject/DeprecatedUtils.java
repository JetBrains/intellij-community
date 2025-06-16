// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.PyPackagesNotificationPanel;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;


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
  /**
   * @param sdkAndException if you have SDK and execution exception provide them here (both must not be null).
   */
  private static void reportPackageInstallationFailure(final @NotNull String frameworkName,
                                                       final @Nullable Pair<Sdk, ExecutionException> sdkAndException) {

    final PyPackageManagementService.PyPackageInstallationErrorDescription errorDescription =
      getErrorDescription(sdkAndException, frameworkName);
    final Application app = ApplicationManager.getApplication();
    app.invokeLater(() -> {
      PyPackagesNotificationPanel.showPackageInstallationError(PyBundle.message("python.new.project.install.failed.title", frameworkName),
                                                               errorDescription);
    });
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

  /**
   * Installs framework and runs callback on success.
   * Installation runs in modal dialog and callback is posted to AWT thread.
   * <p>
   * If "forceInstallFramework" is passed then installs framework in any case.
   * If SDK is remote then checks if it has interpreter and installs if missing
   *
   * @param frameworkName         user-readable framework name (i.e. "Django")
   * @param requirement           name of requirement to install (i.e. "django")
   * @param forceInstallFramework pass true if you are sure required framework is missing
   * @param callback              to be called after installation (or instead of is framework is installed) on AWT thread
   * @return future to be used instead of callback.
   */
  public static @NotNull Future<Void> installFrameworkIfNeeded(final @NotNull Project project,
                                                               final @NotNull String frameworkName,
                                                               final @NotNull String requirement,
                                                               final @NotNull Sdk sdk,
                                                               final boolean forceInstallFramework,
                                                               final @Nullable Runnable callback) {

    var future = new CompletableFuture<Void>();

    // For remote SDK we are not sure if framework exists or not, so we'll check it anyway
    if (forceInstallFramework || PythonSdkUtil.isRemote(sdk)) {

      ProgressManager.getInstance()
        .run(new Task.Modal(project, PyBundle.message("python.install.framework.ensure.installed", frameworkName), false) {
          @Override
          public void run(final @NotNull ProgressIndicator indicator) {
            installPackages(frameworkName, forceInstallFramework, indicator, requirement, sdk);
          }

          @Override
          public void onThrowable(@NotNull Throwable error) {
            future.completeExceptionally(error);
          }

          @Override
          public void onSuccess() {
            future.complete(null);
            // Installed / checked successfully, call callback on AWT
            if (callback != null) {
              callback.run();
            }
          }
        });
    }
    else {
      future.complete(null);
      // No need to install, but still need to call callback on AWT
      if (callback != null) {
        assert SwingUtilities.isEventDispatchThread();
        callback.run();
      }
    }
    return future;
  }

  private static void installPackages(final @NotNull String frameworkName,
                                      boolean forceInstallFramework,
                                      @NotNull ProgressIndicator indicator,
                                      final @NotNull String requirement,
                                      final @NotNull Sdk sdk) {
    final PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
    boolean installed = false;
    if (!forceInstallFramework) {
      // First check if we need to do it
      indicator.setText(PyBundle.message("python.install.framework.checking.is.installed", frameworkName));
      final List<PyPackage> packages = PyPackageUtil.refreshAndGetPackagesModally(sdk);
      installed = PyPsiPackageUtil.findPackage(packages, requirement) != null;
    }

    if (!installed) {
      indicator.setText(PyBundle.message("python.install.framework.installing", frameworkName));
      try {
        packageManager.install(requirement);
        packageManager.refresh();
      }
      catch (final ExecutionException e) {
        reportPackageInstallationFailure(requirement, Pair.create(sdk, e));
      }
    }
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
