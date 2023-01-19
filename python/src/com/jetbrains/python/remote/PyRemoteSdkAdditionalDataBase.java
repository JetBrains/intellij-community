// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote;

import com.intellij.remote.RemoteSdkAdditionalData;
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nullable;

public interface PyRemoteSdkAdditionalDataBase extends RemoteSdkAdditionalData<PyRemoteSdkCredentials>, PyRemoteSdkSkeletonsPathAware,
                                                       PyRemoteSdkAdditionalDataMarker {
  String getVersionString();

  void setVersionString(String versionString);

  @Nullable
  PythonSdkFlavor<?> getFlavor();
}
