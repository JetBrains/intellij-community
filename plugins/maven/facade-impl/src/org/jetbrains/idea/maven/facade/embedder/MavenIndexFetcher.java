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
package org.jetbrains.idea.maven.facade.embedder;

import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.repository.Repository;
import org.jetbrains.idea.maven.facade.MavenFacadeLoggerManager;
import org.sonatype.nexus.index.updater.ResourceFetcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;

public class MavenIndexFetcher implements ResourceFetcher {
  private final String myOriginalRepositoryId;
  private final String myOriginalRepositoryUrl;
  private final WagonManager myWagonManager;
  private final TransferListener myListener;
  private Wagon myWagon = null;

  public MavenIndexFetcher(String originalRepositoryId, String originalRepositoryUrl, WagonManager wagonManager, TransferListener listener) {
    myOriginalRepositoryId = originalRepositoryId;
    myOriginalRepositoryUrl = originalRepositoryUrl;
    myWagonManager = wagonManager;
    myListener = listener;
  }

  public void connect(String _ignoredContextId, String _ignoredUrl) throws IOException {
    String mirrorUrl = myWagonManager.getMirrorRepository(new DefaultArtifactRepository(myOriginalRepositoryId,
                                                                                        myOriginalRepositoryUrl,
                                                                                        null)).getUrl();
    String indexUrl = mirrorUrl + (mirrorUrl.endsWith("/") ? "" : "/") + ".index";
    Repository repository = new Repository(myOriginalRepositoryId, indexUrl);

    try {
      myWagon = myWagonManager.getWagon(repository);
      myWagon.addTransferListener(myListener);

      myWagon.connect(repository,
                      myWagonManager.getAuthenticationInfo(repository.getId()),
                      myWagonManager.getProxy(repository.getProtocol()));
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

  public void disconnect() {
    if (myWagon == null) return;

    try {
      myWagon.disconnect();
    }
    catch (ConnectionException ex) {
      MavenFacadeLoggerManager.getLogger().warn(ex);
    }
  }

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
