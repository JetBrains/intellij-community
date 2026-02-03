// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.rmi.RemoteServer;
import org.jetbrains.idea.maven.server.security.ssl.SslIDEConfirmingTrustStore;

public class RemoteMavenServerBase extends RemoteServer {
  protected static void startMavenServer(MavenServerBase mavenServer, String[] args) throws Exception {
    if (Boolean.getBoolean("delegate.ssl.to.ide")) {
      setupDelegatingSsl();
    }

    start(mavenServer, !RemoteServerUtil.isWSL(), RemoteServerUtil.isDebug(args));
  }

  private static void setupDelegatingSsl() {
    SslIDEConfirmingTrustStore.setup();
  }
}
