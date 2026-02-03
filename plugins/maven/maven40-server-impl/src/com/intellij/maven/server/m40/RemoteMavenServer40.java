// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40;

import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.jetbrains.idea.maven.server.RemoteMavenServerBase;

public class RemoteMavenServer40 extends RemoteMavenServerBase {
  public static void main(String[] args) throws Exception {
    MavenServerUtil.readToken();
    startMavenServer(new Maven40ServerImpl(), args);
  }
}
