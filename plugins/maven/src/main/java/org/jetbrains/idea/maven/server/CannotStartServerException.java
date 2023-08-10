// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

public class CannotStartServerException extends RuntimeException {
  public CannotStartServerException(Throwable cause) {
    super(cause);
  }

  public CannotStartServerException(String cause) {
    super(cause);
  }

  public CannotStartServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
