// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40;

import com.intellij.maven.server.m40.utils.Maven40ModelInheritanceAssembler;
import com.intellij.maven.server.m40.utils.Maven40ProfileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

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

  @NotNull
  @Override
  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven40ProfileUtil.interpolateAndAlignModel(model, basedir);
    }
    catch (Throwable e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven40ModelInheritanceAssembler.assembleInheritance(model, parentModel);
    }
    catch (Throwable e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                MavenExplicitProfiles explicitProfiles,
                                                Collection<String> alwaysOnProfiles, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven40ProfileUtil.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
    }
    catch (Throwable e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }
}
