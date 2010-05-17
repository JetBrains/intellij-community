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
package org.jetbrains.idea.maven.facade.remote;

import org.jetbrains.idea.maven.facade.nexus.ArtifactType;
import org.jetbrains.idea.maven.facade.nexus.RepositoryType;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
public interface MavenFacade extends Remote {

  List<RepositoryType> getRepositories() throws RemoteException;

  List<ArtifactType> findArtifacts(ArtifactType template) throws RemoteException;

  Map<String, List<ArtifactType>> resolveDependencies(List<ArtifactType> artifacts) throws RemoteException;

  void setTransferListener(RemoteTransferListener listener) throws RemoteException;

  void setMavenSettings(MavenFacadeSettings settings) throws RemoteException;

  class MavenFacadeSettings implements Serializable {
    private Repository myLocalRepository;
    private final List<Repository> myRemoteRepositories = new ArrayList<Repository>();
    private final List<String> myNexusUrls = new ArrayList<String>();

    public Repository getLocalRepository() {
      return myLocalRepository;
    }

    public void setLocalRepository(Repository localRepository) {
      myLocalRepository = localRepository;
    }

    public List<Repository> getRemoteRepositories() {
      return myRemoteRepositories;
    }

    public List<String> getNexusUrls() {
      return myNexusUrls;
    }
  }

  class Repository implements Serializable {
    private String myId;
    private String myUrl;
    private String myLayout;

    public Repository(String id, String url, String layout) {
      myUrl = url;
      myId = id;
      myLayout = layout;
    }

    public String getId() {
      return myId;
    }

    public void setId(String id) {
      myId = id;
    }

    public String getUrl() {
      return myUrl;
    }

    public void setUrl(String url) {
      myUrl = url;
    }

    public String getLayout() {
      return myLayout;
    }

    public void setLayout(String layout) {
      myLayout = layout;
    }
  }

}
