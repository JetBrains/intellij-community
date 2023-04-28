// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40;


import com.intellij.execution.rmi.RemoteServer;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.jetbrains.idea.maven.server.RemoteServerUtil;

public class RemoteMavenServer40 extends RemoteServer {
  public static void main(String[] args) throws Exception {
    MavenServerUtil.readToken();
    start(new Maven40ServerImpl(), !RemoteServerUtil.isWSL());
  }
}
