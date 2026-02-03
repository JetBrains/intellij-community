// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.rmi.RemoteServer;
import org.jetbrains.annotations.TestOnly;

@TestOnly
public class RemoteMavenServerThrowsExceptionForTests extends RemoteServer {
  public static void main(String[] args) throws Exception {
    throw new Exception("Maven server exception for tests");
  }
}
