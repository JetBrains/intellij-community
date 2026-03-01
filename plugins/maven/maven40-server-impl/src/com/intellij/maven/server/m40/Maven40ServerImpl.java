// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40;

import org.jetbrains.idea.maven.server.MavenCoreInitializationException;
import org.jetbrains.idea.maven.server.MavenEmbedderSettings;
import org.jetbrains.idea.maven.server.MavenServerBase;
import org.jetbrains.idea.maven.server.MavenServerEmbedder;
import org.jetbrains.idea.maven.server.MavenServerIndexer;
import org.jetbrains.idea.maven.server.MavenServerStatus;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.rmi.server.UnicastRemoteObject;

public class Maven40ServerImpl extends MavenServerBase {
  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Maven40ServerEmbedderImpl result = new Maven40ServerEmbedderImpl(settings);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (MavenCoreInitializationException e) {
      throw e;
    }
    catch (Throwable e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenServerIndexer createIndexer(MavenToken token) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }

  @Override
  public MavenServerStatus getDebugStatus(boolean clean) {
    return new MavenServerStatus();
  }
}
