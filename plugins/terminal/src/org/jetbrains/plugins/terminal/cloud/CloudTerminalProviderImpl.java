// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.impl.runtime.log.CloudTerminalProvider;
import com.intellij.remoteServer.impl.runtime.log.TerminalHandlerBase;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

public final class CloudTerminalProviderImpl extends CloudTerminalProvider {

  @Override
  public @NotNull TerminalHandlerBase createTerminal(@NotNull String presentableName,
                                                     @NotNull Project project,
                                                     @NotNull InputStream terminalOutput,
                                                     @NotNull OutputStream terminalInput) {
    return new TerminalHandlerImpl(presentableName, project, terminalOutput, terminalInput);
  }

  @Override
  public boolean isTtySupported() {
    return true;
  }
}
