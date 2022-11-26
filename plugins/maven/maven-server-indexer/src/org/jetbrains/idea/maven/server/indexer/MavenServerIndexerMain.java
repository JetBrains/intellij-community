// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.indexer;

import com.intellij.execution.rmi.RemoteServer;
import org.jetbrains.idea.maven.server.MavenServerUtil;

public class MavenServerIndexerMain extends RemoteServer {

  public static void main(String[] args) throws Exception {
    MavenServerUtil.readToken();
    start(new MavenServerForIndexer(), true);
  }
}
