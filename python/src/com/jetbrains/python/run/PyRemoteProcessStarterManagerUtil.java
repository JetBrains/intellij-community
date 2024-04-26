// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.UnsupportedPythonSdkTypeException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Koshevoy
 */
public final class PyRemoteProcessStarterManagerUtil {
  private PyRemoteProcessStarterManagerUtil() {
  }

  /**
   * Returns an instance of {@link PyRemoteProcessStarterManager} that
   * corresponds to the provided additional SDK data.
   *
   * @param pyRemoteSdkAdditionalDataBase additional SDK data
   * @return an instance of {@link PyRemoteProcessStarterManager}
   * @throws UnsupportedPythonSdkTypeException if support cannot be found for
   *                                           the type of the provided
   *                                           additional SDK data
   */
  public static @NotNull PyRemoteProcessStarterManager getManager(@NotNull PyRemoteSdkAdditionalDataBase pyRemoteSdkAdditionalDataBase) {
    for (PyRemoteProcessStarterManager processManager : PyRemoteProcessStarterManager.EP_NAME.getExtensions()) {
      if (processManager.supports(pyRemoteSdkAdditionalDataBase)) {
        return processManager;
      }
    }
    throw new UnsupportedPythonSdkTypeException();
  }
}
