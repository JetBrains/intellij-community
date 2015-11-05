package com.jetbrains.python.remote;

import com.intellij.remote.ColoredRemoteProcessHandler;
import com.intellij.remote.RemoteProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public abstract class PyRemoteProcessHandlerBase extends ColoredRemoteProcessHandler<RemoteProcess> implements PyRemoteProcessControl {
  public PyRemoteProcessHandlerBase(@NotNull RemoteProcess process,
                                    @Nullable String commandLine,
                                    @Nullable Charset charset) {
    super(process, commandLine, charset);
  }
}
