// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Collection;

public interface MavenServerConnector extends @NotNull Disposable {
  boolean isSettingsStillValid(MavenWorkspaceSettings settings);

  MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings) throws RemoteException;

  MavenServerIndexer createIndexer() throws RemoteException;

  void addDownloadListener(MavenServerDownloadListener listener);

  void removeDownloadListener(MavenServerDownloadListener listener);

  @NotNull MavenModel interpolateAndAlignModel(MavenModel model, File basedir);

  MavenModel assembleInheritance(MavenModel model, MavenModel parentModel);

  ProfileApplicationResult applyProfiles(MavenModel model,
                                         File basedir,
                                         MavenExplicitProfiles explicitProfiles,
                                         Collection<String> alwaysOnProfiles);

  void shutdown(boolean wait);

  default <R, E extends Exception> R perform(RemoteObjectWrapper.Retriable<R, E> r) throws E {
    try {
      return r.execute();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  void dispose();

  @NotNull Sdk getJdk();

  MavenDistribution getMavenDistribution();

  String getVMOptions();
}
