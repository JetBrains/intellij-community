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
package org.jetbrains.idea.maven.facade.remote.impl;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.*;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.RuntimeInfo;
import org.apache.maven.settings.Settings;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.nexus.ArtifactType;
import org.jetbrains.idea.maven.facade.nexus.Endpoint;
import org.jetbrains.idea.maven.facade.nexus.RepositoryType;
import org.jetbrains.idea.maven.facade.nexus.SearchResults;
import org.jetbrains.idea.maven.facade.remote.MavenFacade;
import org.jetbrains.idea.maven.facade.remote.RemoteTransferListener;

import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class MavenFacadeImpl extends RemoteImpl implements MavenFacade {

  private static final DefaultPlexusContainer ourContainer = initializeMaven();
  private MavenFacadeSettings mySettings;

  public static DefaultPlexusContainer initializeMaven() {
    DefaultPlexusContainer container;
    try {
      container = new DefaultPlexusContainer();
    }
    catch (RuntimeException e) {
      String s = "Cannot initialize Maven. Please make sure that your IDEA installation is correct and has no old libraries.";
      throw new RuntimeException(s, e);
    }

    container.setClassWorld(new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader()));
//        CustomLoggerManager loggerManager = new CustomLoggerManager(generalSettings.getLoggingLevel());
//        container.setLoggerManager(loggerManager);

    try {
      container.initialize();
      container.start();
    }
    catch (PlexusContainerException e) {
      throw new RuntimeException(e);
    }

//        System.setProperty("maven.home", "<...>");
//        System.setProperty(MavenSettingsBuilder.ALT_GLOBAL_SETTINGS_XML_LOCATION, "<..>");

    Settings settings = null;

    try {
      MavenSettingsBuilder builder = (MavenSettingsBuilder)container.lookup(MavenSettingsBuilder.ROLE);
//            File userSettingsFile = generalSettings.getEffectiveUserSettingsIoFile();
//            if (userSettingsFile != null && userSettingsFile.exists() && !userSettingsFile.isDirectory()) {
//                settings = builder.buildSettings(userSettingsFile, false);
//            }
      if (settings == null) {
        settings = builder.buildSettings();
      }
    }
    catch (ComponentLookupException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    catch (XmlPullParserException e) {
      e.printStackTrace();
    }

    if (settings == null) {
      settings = new Settings();
    }

//        settings.setLocalRepository("<...>");

    settings.setOffline(false);
    settings.setInteractiveMode(false);
    settings.setUsePluginRegistry(false);

    RuntimeInfo runtimeInfo = new RuntimeInfo(settings);
//        runtimeInfo.setPluginUpdateOverride(generalSettings.getPluginUpdatePolicy() == MavenExecutionOptions.PluginUpdatePolicy.UPDATE);
    settings.setRuntimeInfo(runtimeInfo);

    return container;

  }

  public List<RepositoryType> getRepositories(String nexusUrl) throws RemoteException {
    try {
      return new Endpoint.Repositories(nexusUrl).getRepolistAsRepositories().getData().getRepositoriesItem();
    }
    catch (Exception ex) {
      handleException(ex);
      throw new AssertionError();
    }
  }

  @Nullable
  public List<ArtifactType> findArtifacts(ArtifactType template, String nexusUrl) throws RemoteException {
    try {
      SearchResults results = new Endpoint.DataIndex(nexusUrl)
        .getArtifactlistAsSearchResults(null, template.getGroupId(), template.getArtifactId(), template.getVersion(),
                                        template.getClassifier(), template.getContextId());
      final boolean canTrySwitchGAV = template.getArtifactId() == null && template.getGroupId() != null;
      final boolean tooManyResults = results.isTooManyResults();
      if (canTrySwitchGAV && (tooManyResults || BigInteger.ZERO.equals(results.getTotalCount()))) {
        results = new Endpoint.DataIndex(nexusUrl)
          .getArtifactlistAsSearchResults(null, null, template.getGroupId(), template.getVersion(),
                                          template.getClassifier(), template.getContextId());
      }
      if (tooManyResults || results.isTooManyResults()) return null;
      return new ArrayList<ArtifactType>(results.getData().getArtifact());
    }
    catch (Exception ex) {
      handleException(ex);
      throw new AssertionError();
    }
  }

  private void addMissingVersions(final Map<String, ArtifactType> result) {
    try {
      final ArtifactMetadataSource metadataSource = (ArtifactMetadataSource)ourContainer.lookup(ArtifactMetadataSource.ROLE);
      final ArtifactFactory artifactFactory = (ArtifactFactory)ourContainer.lookup(ArtifactFactory.ROLE);
      final ArtifactRepository localRepo = getRepository(mySettings.getLocalRepository());
      final List<ArtifactRepository> remoteRepos = new ArrayList<ArtifactRepository>();
      for (Repository repository : mySettings.getRemoteRepositories()) {
        remoteRepos.add(getRepository(repository));
      }
      final HashSet<String> visitedIds = new HashSet<String>();
      for (ArtifactType artifactType : result.values()) {
        if (!visitedIds.add(artifactType.getGroupId()+":"+artifactType.getArtifactId())) continue;
        if (artifactType.getPackaging() == null) continue;
        final Artifact artifact = createArtifact(artifactFactory, artifactType);
        try {
          final List<ArtifactVersion> list = metadataSource.retrieveAvailableVersions(artifact, localRepo, remoteRepos);
          for (ArtifactVersion version : list) {
            final String coord = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + version;
            if (!result.containsKey(coord)) {
              final ArtifactType type = new ArtifactType(artifactType.getGroupId(), artifactType.getArtifactId(), version.toString());
              type.setPackaging(artifact.getType());
              result.put(coord, type);
            }
          }
        }
        catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    }
    catch (Exception e) {
      handleException(e);
      throw new AssertionError();
    }
  }

  public Map<String, List<ArtifactType>> resolveDependencies(List<ArtifactType> artifacts) throws RemoteException {
    try {
      return resolveDependenciesInner(artifacts);
    }
    catch (Exception e) {
      handleException(e);
      throw new AssertionError();
    }
  }

  public void setTransferListener(final RemoteTransferListener listener) throws RemoteException {
    try {
      final WagonManager wagonManager = (WagonManager)ourContainer.lookup(WagonManager.ROLE);
      if (listener == null) {
        wagonManager.setDownloadMonitor(null);
        return;
      }
      wagonManager.setDownloadMonitor(new TransferListener() {
        public void transferInitiated(TransferEvent transferEvent) {
          try {
            listener.transferInitiated(convert(transferEvent));
          }
          catch (RemoteException ignored) { }
        }

        public void transferStarted(TransferEvent transferEvent) {
          try {
            listener.transferStarted(convert(transferEvent));
          }
          catch (RemoteException ignored) {
          }
        }

        public void transferProgress(TransferEvent transferEvent, byte[] bytes, int i) {
          try {
            listener.transferProgress(convert(transferEvent), i);
          }
          catch (RemoteException ignored) {
          }
        }

        public void transferCompleted(TransferEvent transferEvent) {
          try {
            listener.transferCompleted(convert(transferEvent));
          }
          catch (RemoteException ignored) {
          }
        }

        public void transferError(TransferEvent transferEvent) {
          try {
            listener.transferError(convert(transferEvent));
          }
          catch (RemoteException ignored) {
          }
        }

        public void debug(String s) {
          try {
            listener.debug(s);
          }
          catch (RemoteException ignored) {
          }
        }
      });
    }
    catch (Exception e) {
      handleException(e);
      throw new AssertionError();
    }
  }

  public void setMavenSettings(MavenFacadeSettings settings) throws RemoteException {
    mySettings = settings;
  }

  private static RemoteTransferListener.TransferEvent convert(TransferEvent transferEvent) {
    final RemoteTransferListener.TransferEvent.EventType et;
    switch (transferEvent.getEventType()) {
      case TransferEvent.TRANSFER_INITIATED: et = RemoteTransferListener.TransferEvent.EventType.INITIATED; break;
      case TransferEvent.TRANSFER_STARTED: et = RemoteTransferListener.TransferEvent.EventType.STARTED; break;
      case TransferEvent.TRANSFER_PROGRESS: et = RemoteTransferListener.TransferEvent.EventType.PROGRESS; break;
      case TransferEvent.TRANSFER_COMPLETED: et = RemoteTransferListener.TransferEvent.EventType.COMPLETED; break;
      case TransferEvent.TRANSFER_ERROR: et = RemoteTransferListener.TransferEvent.EventType.ERROR; break;
      default: throw new AssertionError(transferEvent.getEventType());
    }
    final RemoteTransferListener.TransferEvent.RequestType reqType = transferEvent.getRequestType() == TransferEvent.REQUEST_GET
                                                                     ? RemoteTransferListener.TransferEvent.RequestType.GET
                                                                     : RemoteTransferListener.TransferEvent.RequestType.PUT;
    final RemoteTransferListener.Resource resource = new RemoteTransferListener.Resource(transferEvent.getResource().getName(),
                                                                                         transferEvent.getResource().getLastModified(),
                                                                                         transferEvent.getResource().getContentLength());
    return new RemoteTransferListener.TransferEvent(transferEvent.getLocalFile(), et, reqType, resource, transferEvent.getException(), transferEvent.getWagon().getRepository().getUrl());
  }


  private Map<String, List<ArtifactType>> resolveDependenciesInner(List<ArtifactType> artifactsToResolve) throws Exception {
    final ArtifactResolver resolver = (ArtifactResolver)ourContainer.lookup(ArtifactResolver.ROLE);
    final ArtifactFactory artifactFactory = (ArtifactFactory)ourContainer.lookup(ArtifactFactory.ROLE);
    final ArtifactMetadataSource metadataSource = (ArtifactMetadataSource)ourContainer.lookup(ArtifactMetadataSource.ROLE);

    final ArtifactRepository localRepo = getRepository(mySettings.getLocalRepository());
    final List<ArtifactRepository> remoteRepos = new ArrayList<ArtifactRepository>();
    for (Repository repository : mySettings.getRemoteRepositories()) {
      remoteRepos.add(getRepository(repository));
    }


    final Artifact project = artifactFactory.createBuildArtifact("local", "project", "1.0", "pom");
    final Set<Artifact> toResolve = new HashSet<Artifact>();
    for (ArtifactType template : artifactsToResolve) {
      toResolve.add(createArtifact(artifactFactory, template));
    }

    final Map<String, List<ArtifactType>> resultMap = new HashMap<String, List<ArtifactType>>();
    for (Artifact artifact : toResolve) {
      try {
        final ArtifactResolutionResult result = resolver.resolveTransitively(
          Collections.singleton(artifact), project, Collections.EMPTY_MAP, localRepo, remoteRepos,
          metadataSource, new ScopeArtifactFilter(DefaultArtifact.SCOPE_RUNTIME));
        resultMap.put(getCoordinate(artifact), toArtifactTypeList(result.getArtifacts()));
      }
      catch (MultipleArtifactsNotFoundException e) {
        resultMap.put(getCoordinate(artifact), toArtifactTypeList(e.getResolvedArtifacts()));
        //System.out.println("Missing: -------------------");
        //toArtifactTypeList(e.getMissingArtifacts());
      }
      catch (ArtifactResolutionException e) {
        //throw e;
      }
      catch (ArtifactNotFoundException e) {
        //throw e;
        //System.out.println("Missing: -------------------");
        //toArtifactTypeList(Collections.singletonList(e.getArtifact()));
      }
    }
    return resultMap;
  }

  private Artifact createArtifact(ArtifactFactory artifactFactory, ArtifactType template) {
    return artifactFactory.createDependencyArtifact(
          template.getGroupId(),
          template.getArtifactId(),
          template.getVersion() == null? null : VersionRange.createFromVersion(template.getVersion()),
          template.getPackaging(),
          null,
          "runtime");
  }

  private static ArtifactRepository getRepository(Repository r) throws ComponentLookupException {
    final ArtifactRepositoryLayout repoLayout = (ArtifactRepositoryLayout)ourContainer.lookup(ArtifactRepositoryLayout.ROLE, r.getLayout());
    return new DefaultArtifactRepository(r.getId(), r.getUrl(), repoLayout);
  }

  private static Map<String, List<ArtifactType>> toArtifactTypeMap(Collection<Artifact> toResolve, Collection<Artifact> resolvedArtifacts) {
    final Map<String, List<ArtifactType>> result = new HashMap<String, List<ArtifactType>>();
    final Map<String, String> idMap = new HashMap<String, String>();
    for (Artifact artifact : toResolve) {
      idMap.put(artifact.getId(), getCoordinate(artifact));
      result.put(getCoordinate(artifact), new ArrayList<ArtifactType>());
    }
    for (Artifact artifact : resolvedArtifacts) {
      List<ArtifactType> list = null;
      for (String s : artifact.getDependencyTrail()) {
        list = result.get(idMap.get(s));
        if (list != null) {
          list.add(toArtifactType(artifact));
          break;
        }
      }
      if (list == null) {
        throw new AssertionError(getCoordinate(artifact));
      }
    }
    return result;
  }

  private static String getCoordinate(Artifact artifact) {
    return artifact.getGroupId()+":"+artifact.getArtifactId()+":"+artifact.getVersion();
  }

  private static List<ArtifactType> toArtifactTypeList(Collection<Artifact> artifacts) throws MalformedURLException {
    final ArrayList<ArtifactType> result = new ArrayList<ArtifactType>(artifacts.size());
    for (Artifact artifact : artifacts) {
      result.add(toArtifactType(artifact));
    }
    return result;
  }

  private static ArtifactType toArtifactType(Artifact artifact) {
    final ArtifactType type = new ArtifactType();
    type.setGroupId(artifact.getGroupId());
    type.setArtifactId(artifact.getArtifactId());
    type.setVersion(artifact.getVersion());
    type.setClassifier(artifact.getClassifier());
    type.setResourceUri(artifact.getFile().toURI().toASCIIString());
    return type;
  }
}
