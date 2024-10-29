// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.google.common.net.HostAndPort;
import com.intellij.openapi.util.Pair;
import com.intellij.remote.ProcessControlWithMappings;
import com.intellij.remote.RemoteSdkException;
import org.jetbrains.annotations.Nullable;

public interface RemoteProcessControl extends ProcessControlWithMappings {
  Pair<String, Integer> getRemoteSocket(int localPort) throws RemoteSdkException;

  @Nullable HostAndPort getLocalTunnel(int remotePort);
}
