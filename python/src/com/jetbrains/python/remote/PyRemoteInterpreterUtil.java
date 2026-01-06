// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.Key;
import com.intellij.remote.RemoteSdkException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyExecutionException;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class PyRemoteInterpreterUtil {

  private static @NotNull RemoteSdkException createException(final @NotNull ProcessOutput processOutput, String @NotNull [] command) {
    return RemoteSdkException.cantObtainRemoteCredentials(
      new PyExecutionException(PyBundle.message("python.sdk.can.t.obtain.python.version"),
                               command[0],
                               Lists.newArrayList(command),
                               processOutput));
  }

  public static void closeOnProcessTermination(@NotNull ProcessHandler processHandler, @NotNull Closeable closeable) {
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        // Nothing.
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        try {
          closeable.close();
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        // Nothing.
      }
    });
  }
}
