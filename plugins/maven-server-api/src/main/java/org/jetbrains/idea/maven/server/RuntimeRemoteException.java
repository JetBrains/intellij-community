// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import java.rmi.RemoteException;

public class RuntimeRemoteException extends RuntimeException {
  public RuntimeRemoteException(RemoteException cause) {
    super(cause);
  }

  @Override
  public RemoteException getCause() {
    return (RemoteException)super.getCause();
  }
}
