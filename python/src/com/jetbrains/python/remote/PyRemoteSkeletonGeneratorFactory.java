/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  public final static ExtensionPointName<PyRemoteSkeletonGeneratorFactory> EP_NAME
    = ExtensionPointName.create("Pythonid.remoteSkeletonGeneratorFactory");

  public abstract boolean supports(@NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData);

  public abstract PySkeletonGenerator createRemoteSkeletonGenerator(@Nullable Project project,
                                                                    @Nullable Component ownerComponent,
                                                                    @NotNull Sdk sdk,
                                                                    String skeletonPath) throws ExecutionException;

  @NotNull
  public static PyRemoteSkeletonGeneratorFactory getInstance(@NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData) {
    for (PyRemoteSkeletonGeneratorFactory manager : EP_NAME.getExtensions()) {
      if (manager.supports(sdkAdditionalData)) {
        return manager;
      }
    }
    throw new IllegalStateException("Failed to find extension " + EP_NAME + " that supports " + sdkAdditionalData);
  }
}
