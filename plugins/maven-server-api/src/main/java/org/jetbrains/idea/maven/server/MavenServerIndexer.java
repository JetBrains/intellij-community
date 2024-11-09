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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenIndexId;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.nio.file.Path;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;

public interface MavenServerIndexer extends Remote {
  String SEARCH_TERM_CLASS_NAMES = "c"; // see org.sonatype.nexus.index.ArtifactInfo

  void releaseIndex(MavenIndexId mavenIndexId, MavenToken token) throws RemoteException, MavenServerIndexerException;

  int getIndexCount(MavenToken token) throws RemoteException;

  void updateIndex(MavenIndexId mavenIndexId, MavenServerProgressIndicator indicator, boolean multithreaded, MavenToken token)
    throws RemoteException, MavenServerIndexerException, MavenServerProcessCanceledException;

  @Nullable
    //null means no artifacts lasts
  ArrayList<IndexedMavenId> processArtifacts(MavenIndexId mavenIndexId, int startFrom, MavenToken token)
    throws RemoteException, MavenServerIndexerException;

  @NotNull
  ArrayList<AddArtifactResponse> addArtifacts(@NotNull MavenIndexId mavenIndexId, @NotNull ArrayList<Path> artifactFiles, MavenToken token)
    throws RemoteException, MavenServerIndexerException;

  HashSet<MavenArtifactInfo> search(MavenIndexId mavenIndexId, String pattern, int maxResult, MavenToken token)
    throws RemoteException, MavenServerIndexerException;

  HashSet<MavenArchetype> getInternalArchetypes(MavenToken token) throws RemoteException;

  void release(MavenToken token) throws RemoteException;

  boolean indexExists(Path dir, MavenToken token) throws RemoteException;
}
