/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.repository.Repository;
import org.sonatype.nexus.index.updater.AbstractResourceFetcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;

public class Maven3ServerIndexFetcher extends AbstractResourceFetcher {
  private final String myOriginalRepositoryId;
  private final String myOriginalRepositoryUrl;
  private final WagonManager myWagonManager;
  private final RepositorySystem myRepositorySystem;
  private final TransferListener myListener;
  private Wagon myWagon = null;

  public Maven3ServerIndexFetcher(String originalRepositoryId,
                                  String originalRepositoryUrl,
                                  WagonManager wagonManager,
                                  RepositorySystem repositorySystem,
                                  TransferListener listener) {
    myOriginalRepositoryId = originalRepositoryId;
    myOriginalRepositoryUrl = originalRepositoryUrl;
    myWagonManager = wagonManager;
    myRepositorySystem = repositorySystem;
    myListener = listener;
  }

  @Override
  public void connect(String _ignoredContextId, String _ignoredUrl) throws IOException {
    ArtifactRepository artifactRepository =
      myRepositorySystem.createArtifactRepository(myOriginalRepositoryId, myOriginalRepositoryUrl, null, null, null);

    final ArtifactRepository mirrorRepository = myWagonManager.getMirrorRepository(artifactRepository);
    String mirrorUrl = mirrorRepository.getUrl();
    String indexUrl = mirrorUrl + (mirrorUrl.endsWith("/") ? "" : "/") + ".index";
    Repository repository = new Repository(myOriginalRepositoryId, indexUrl);

    try {
      myWagon = myWagonManager.getWagon(repository);
      myWagon.addTransferListener(myListener);

      myWagon.connect(repository,
                      myWagonManager.getAuthenticationInfo(mirrorRepository.getId()),
                      myWagonManager.getProxy(mirrorRepository.getProtocol()));
    }
    catch (AuthenticationException e) {
      IOException newEx = new IOException("Authentication exception connecting to " + repository);
      newEx.initCause(e);
      throw newEx;
    }
    catch (WagonException e) {
      IOException newEx = new IOException("Wagon exception connecting to " + repository);
      newEx.initCause(e);
      throw newEx;
    }
  }

  @Override
  public void disconnect() throws RemoteException {
    if (myWagon == null) return;

    try {
      myWagon.disconnect();
    }
    catch (ConnectionException ex) {
      Maven3ServerGlobals.getLogger().warn(ex);
    }
  }

  @Override
  public void retrieve(String name, File targetFile) throws IOException {
    try {
      myWagon.get(name, targetFile);
    }
    catch (AuthorizationException e) {
      IOException newEx = new IOException("Authorization exception retrieving " + name);
      newEx.initCause(e);
      throw newEx;
    }
    catch (ResourceDoesNotExistException e) {
      IOException newEx = new FileNotFoundException("Resource " + name + " does not exist");
      newEx.initCause(e);
      throw newEx;
    }
    catch (WagonException e) {
      IOException newEx = new IOException("Transfer for " + name + " failed");
      newEx.initCause(e);
      throw newEx;
    }
  }
}
