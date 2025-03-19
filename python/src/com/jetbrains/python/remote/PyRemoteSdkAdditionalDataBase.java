// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.remote.RemoteSdkAdditionalData;
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nullable;

public interface PyRemoteSdkAdditionalDataBase
  extends RemoteSdkAdditionalData, PyRemoteSdkSkeletonsPathAware, PyRemoteSdkAdditionalDataMarker
{
  String getVersionString();

  void setVersionString(String versionString);

  @Nullable PythonSdkFlavor<?> getFlavor();
}
