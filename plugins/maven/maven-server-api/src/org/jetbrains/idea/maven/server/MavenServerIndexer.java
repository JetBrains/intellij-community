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

import org.apache.lucene.search.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Set;

public interface MavenServerIndexer extends Remote {
  String SEARCH_TERM_CLASS_NAMES = "c"; // see org.sonatype.nexus.index.ArtifactInfo

  int createIndex(@NotNull String indexId,
                  @NotNull String repositoryId,
                  @Nullable File file,
                  @Nullable String url,
                  @NotNull File indexDir) throws RemoteException, MavenServerIndexerException;

  void releaseIndex(int id) throws RemoteException, MavenServerIndexerException;

  int getIndexCount() throws RemoteException;

  void updateIndex(int id, MavenServerSettings settings, MavenServerProgressIndicator indicator) throws RemoteException,
                                                                                                        MavenServerIndexerException,
                                                                                                        MavenServerProcessCanceledException;

  void processArtifacts(int indexId, MavenServerIndicesProcessor processor) throws RemoteException, MavenServerIndexerException;

  IndexedMavenId addArtifact(int indexId, File artifactFile) throws RemoteException, MavenServerIndexerException;

  Set<MavenArtifactInfo> search(int indexId, Query query, int maxResult) throws RemoteException, MavenServerIndexerException;

  Collection<MavenArchetype> getArchetypes() throws RemoteException;

  void release() throws RemoteException;

  boolean indexExists(File dir) throws RemoteException;
}
