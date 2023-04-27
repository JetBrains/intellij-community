/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import com.intellij.execution.rmi.IdeaWatchdogAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;

public interface MavenServer extends Remote, IdeaWatchdogAware {

  MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings, MavenToken token) throws RemoteException;

  MavenServerIndexer createIndexer(MavenToken token) throws RemoteException;

  @NotNull
  MavenModel interpolateAndAlignModel(MavenModel model, File basedir, MavenToken token) throws RemoteException;

  MavenModel assembleInheritance(MavenModel model, MavenModel parentModel, MavenToken token) throws RemoteException;

  ProfileApplicationResult applyProfiles(MavenModel model,
                                         File basedir,
                                         MavenExplicitProfiles explicitProfiles,
                                         Collection<String> alwaysOnProfiles, MavenToken token) throws RemoteException;

  @Nullable
  MavenPullServerLogger createPullLogger(MavenToken token) throws RemoteException;

  @Nullable
  MavenPullDownloadListener createPullDownloadListener(MavenToken token) throws RemoteException;

  boolean ping(MavenToken token) throws RemoteException;
}
