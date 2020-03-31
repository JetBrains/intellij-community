// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote;

import com.google.common.base.Function;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.remote.RemoteConnectionCredentialsWrapper;
import com.intellij.remote.RemoteCredentials;
import com.intellij.remote.RemoteSdkCredentials;
import com.intellij.remote.RemoteSdkCredentialsProducer;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.Supplier;

/**
 * This service provides functionality for SSH remote interpreters. It is
 * expected to be implemented in Python SSH Interpreter plugin using the
 * extension point {@link #EP_NAME}.
 */
public interface PythonSshInterpreterManager {
  Logger LOG = Logger.getInstance(PythonSshInterpreterManager.class);

  ExtensionPointName<PythonSshInterpreterManager> EP_NAME = ExtensionPointName.create("Pythonid.sshInterpreterManager");

  void copyFromRemote(@NotNull Project project,
                      @NotNull RemoteSdkCredentials data,
                      @NotNull List<PathMappingSettings.PathMapping> mappings);

  /**
   * Creates form to browse remote box.
   * You need to show it to user using dialog.
   *
   * @return null if remote sdk can't be browsed.
   * First argument is consumer to get path, chosen by user.
   * Second is panel to display to user
   * @throws ExecutionException   credentials can't be obtained due to remote server error
   * @throws InterruptedException credentials can't be obtained due to remote server error
   */
  @Nullable
  Pair<Supplier<String>, JPanel> createServerBrowserForm(@NotNull final Sdk remoteSdk)
    throws ExecutionException, InterruptedException;


  @NotNull
  RemoteSdkCredentialsProducer<PyRemoteSdkCredentials> getRemoteSdkCredentialsProducer(@NotNull Function<RemoteCredentials, PyRemoteSdkCredentials> credentialsTransformer,
                                                                                       @NotNull RemoteConnectionCredentialsWrapper connectionWrapper);

  class Factory {
    @Nullable
    public static PythonSshInterpreterManager getInstance() {
      PythonSshInterpreterManager[] extensions = EP_NAME.getExtensions();
      if (extensions.length == 0) {
        LOG.debug(MessageFormat.format("Extension for ''{0}'' extension point is absent", EP_NAME.getName()));
        return null;
      }
      else if (extensions.length == 1) {
        return extensions[0];
      }
      else {
        LOG.error(MessageFormat.format("Several extensions registered for ''{0}'' extension point", EP_NAME.getName()));
        return null;
      }
    }
  }
}
