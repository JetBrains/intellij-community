// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.skeletons.PySkeletonGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Koshevoy
 */
public abstract class PyRemoteSkeletonGeneratorFactory {
  public static final ExtensionPointName<PyRemoteSkeletonGeneratorFactory> EP_NAME
    = ExtensionPointName.create("Pythonid.remoteSkeletonGeneratorFactory");

  public abstract boolean supports(@NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData);

  public abstract PySkeletonGenerator createRemoteSkeletonGenerator(@Nullable Project project,
                                                                    @Nullable Component ownerComponent,
                                                                    @NotNull Sdk sdk,
                                                                    @NotNull String skeletonPath) throws ExecutionException;

  /**
   * Returns an instance of {@link PyRemoteSkeletonGeneratorFactory} that
   * corresponds to the provided additional SDK data.
   *
   * @param sdkAdditionalData additional SDK data
   * @return an instance of {@link PyRemoteSkeletonGeneratorFactory}
   * @throws UnsupportedPythonSdkTypeException if support cannot be found for
   *                                           the type of the provided
   *                                           additional SDK data
   */
  public static @NotNull PyRemoteSkeletonGeneratorFactory getInstance(@NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData) {
    for (PyRemoteSkeletonGeneratorFactory manager : EP_NAME.getExtensions()) {
      if (manager.supports(sdkAdditionalData)) {
        return manager;
      }
    }
    throw new UnsupportedPythonSdkTypeException();
  }
}
