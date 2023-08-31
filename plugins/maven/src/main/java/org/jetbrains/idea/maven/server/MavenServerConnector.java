// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;

import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

public interface MavenServerConnector extends Disposable {
  Topic<MavenServerDownloadListener> DOWNLOAD_LISTENER_TOPIC =
    new Topic<>(MavenServerDownloadListener.class.getSimpleName(), MavenServerDownloadListener.class);

  boolean isCompatibleWith(Sdk jdk, String vmOptions, MavenDistribution distribution);

  @ApiStatus.Internal
  boolean isNew();

  @ApiStatus.Internal
  void connect();

  boolean addMultimoduleDir(String multimoduleDirectory);

  MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings) throws RemoteException;

  MavenServerIndexer createIndexer() throws RemoteException;

  @NotNull MavenModel interpolateAndAlignModel(MavenModel model, Path basedir);

  MavenModel assembleInheritance(MavenModel model, MavenModel parentModel);

  ProfileApplicationResult applyProfiles(MavenModel model,
                                         Path basedir,
                                         MavenExplicitProfiles explicitProfiles,
                                         Collection<String> alwaysOnProfiles);

  @ApiStatus.Internal
  boolean ping();

  String getSupportType();

  State getState();

  boolean checkConnected();

  @ApiStatus.Internal
  void stop(boolean wait);

  @NotNull Sdk getJdk();

  @NotNull MavenDistribution getMavenDistribution();

  String getVMOptions();

  @Nullable Project getProject();

  List<String> getMultimoduleDirectories();

  MavenServerStatus getDebugStatus(boolean clean);

  enum State {
    STARTING,
    RUNNING,
    FAILED,
    STOPPED
  }
}
