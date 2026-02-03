// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote;

/**
 * The exception is thrown when no implementation of
 * {@link PythonRemoteInterpreterManager#EP_NAME} extension point is available,
 * what means that Python SSH Interpreter plugin is disabled by user (or it
 * was failed to load because of internal problems).
 */
public class PythonRemoteInterpreterPluginNotAvailableException extends RuntimeException {
  private static final String ERROR_MESSAGE = "Remote interpreter can't be executed. Please enable the Python SSH Interpreter plugin.";

  public PythonRemoteInterpreterPluginNotAvailableException() {
    super(ERROR_MESSAGE);
  }
}
