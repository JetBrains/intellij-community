/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * It is discouraged to use this class as it violates LSP.
 *
 * @deprecated to get all pythons on system, use {@link com.intellij.python.community.services.systemPython.SystemPythonService}.
 * To get all python SDKs, use {@link PythonSdkUtil#getAllSdks()}
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public final class PyDetectedSdk extends ProjectJdkImpl {
  public PyDetectedSdk(@NotNull String name) {
    super(name, PythonSdkType.getInstance(), name, "");
  }

  @Override
  public String getVersionString() {
    return "";
  }

  /**
   * This method was designed to avoid instanceof checks and provide a more type-safe way to cast Sdk to PyDetectedSdk.
   * @param sdk - Sdk to be casted
   * @return PyDetectedSdk if sdk itself or delegate of PyRichSdk is instance of PyDetectedSdk, null otherwise
   */
  @ApiStatus.Obsolete
  public static @Nullable PyDetectedSdk asPyDetectedSdk(Sdk sdk) {
    if (sdk instanceof PyRichSdk richSdk) {
      return asPyDetectedSdk(richSdk.getSdk());
    }
    if (sdk instanceof PyDetectedSdk detectedSdk) {
      return detectedSdk;
    }
    return null;
  }
}
