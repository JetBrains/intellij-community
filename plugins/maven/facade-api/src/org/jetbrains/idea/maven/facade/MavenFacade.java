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
package org.jetbrains.idea.maven.facade;

import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

public interface MavenFacade extends Remote {
  void setLogger(MavenFacadeLogger logger) throws RemoteException;

  MavenFacadeEmbedder createEmbedder(MavenFacadeSettings settings) throws RemoteException;

  MavenFacadeIndexer createIndexer() throws RemoteException;

  MavenModel interpolateAndAlignModel(MavenModel model, File basedir) throws RemoteException;

  MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) throws RemoteException;

  ProfileApplicationResult applyProfiles(MavenModel model,
                                         File basedir,
                                         Collection<String> explicitProfiles,
                                         Collection<String> alwaysOnProfiles) throws RemoteException;

  List<MavenRepositoryInfo> getRepositories(String nexusUrl) throws RemoteException;

  List<MavenArtifactInfo> findArtifacts(MavenArtifactInfo template, String nexusUrl) throws RemoteException;
}
