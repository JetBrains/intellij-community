// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote;

import com.intellij.remote.ColoredRemoteProcessHandler;
import com.intellij.remote.RemoteProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public abstract class PyRemoteProcessHandlerBase extends ColoredRemoteProcessHandler<RemoteProcess> implements PyRemoteProcessControl {
  public PyRemoteProcessHandlerBase(@NotNull RemoteProcess process,
                                    @NotNull String commandLine,
                                    @Nullable Charset charset) {
    super(process, commandLine, charset);
  }
}
