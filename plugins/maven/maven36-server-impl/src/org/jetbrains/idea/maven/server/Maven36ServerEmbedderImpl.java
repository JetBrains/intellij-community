// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import java.rmi.RemoteException;

public class Maven36ServerEmbedderImpl extends Maven3XServerEmbedder {
  public Maven36ServerEmbedderImpl(MavenEmbedderSettings settings) throws RemoteException {
    super(settings);
  }

  @Override
  protected void initLogging(Maven3ServerConsoleLogger consoleWrapper) {
    Maven3Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }
}

