// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jetbrains.idea.maven.server.utils.MavenServerParallelRunner;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class CustomMaven36ArtifactResolver implements ArtifactResolver {
  private final ArtifactResolver myWrapee;
  private final Map<ArtifactRequestData, ArtifactResultData> artifactCache = new ConcurrentHashMap<>();

  public CustomMaven36ArtifactResolver(ArtifactResolver wrapee) {
    myWrapee = wrapee;
  }

  private static <T> T rethrow(Supplier<T> supplier) throws ArtifactResolutionException {
    try {
      return supplier.get();
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ArtifactResolutionException) {
        throw (ArtifactResolutionException)cause;
      }
      throw e;
    }
  }

  private ArtifactResult doResolveArtifactAndWrapException(RepositorySystemSession session, ArtifactRequest request) {
    try {
      return myWrapee.resolveArtifact(session, request);
    }
    catch (ArtifactResolutionException e) {
      throw new RuntimeException(e);
    }
  }

  private ArtifactResult doResolveArtifact(RepositorySystemSession session, ArtifactRequest request) {
    if (null == request) return null;

    ArtifactRequestData requestData = getData(request);

    ArtifactResultData resultData = artifactCache.computeIfAbsent(requestData, rd -> getData(doResolveArtifactAndWrapException(session, request)));

    ArtifactResult result = new ArtifactResult(request);
    result.setArtifact(resultData.getArtifact());
    result.setRepository(resultData.getRepository());
    List<Exception> exceptions = resultData.getExceptions();
    if (null != exceptions) {
      for (Exception exception : exceptions) {
        result.addException(exception);
      }
    }
    return result;
  }

  private List<ArtifactResult> doResolveArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests) {
    if (null == requests) return null;

    Collection<ArtifactResult> results = MavenServerParallelRunner.execute(
      true,
      requests,
      request -> doResolveArtifact(session, request)
    );

    return new ArrayList<>(results);
  }

  @Override
  public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request) throws ArtifactResolutionException {
    return rethrow(() -> doResolveArtifact(session, request));
  }

  @Override
  public List<ArtifactResult> resolveArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)
    throws ArtifactResolutionException {
    return rethrow(() -> doResolveArtifacts(session, requests));
  }

  public void reset() {
    artifactCache.clear();
  }

  private static ArtifactRequestData getData(ArtifactRequest request) {
    Artifact artifact = request.getArtifact();
    return new ArtifactRequestData(
      artifact.getArtifactId(),
      artifact.getGroupId(),
      artifact.getVersion(),
      artifact.getClassifier(),
      artifact.getExtension(),
      getData(request.getRepositories())
    );
  }

  private static List<RepositoryData> getData(List<RemoteRepository> repositories) {
    if (null == repositories) return null;
    List<RepositoryData> list = new ArrayList<>();
    for (RemoteRepository repo : repositories) {
      RepositoryData data = getData(repo);
      list.add(data);
    }
    return list;
  }

  private static RepositoryData getData(RemoteRepository repository) {
    return new RepositoryData(repository.getId(), repository.getUrl());
  }

  private static ArtifactResultData getData(ArtifactResult result) {
    return new ArtifactResultData(result.getArtifact(), result.getRepository(), result.getExceptions());
  }

  private static class ArtifactRequestData {
    private final String artifactId;
    private final String groupId;
    private final String version;
    private final String classifier;
    private final String extension;
    private final List<RepositoryData> repositories;

    private ArtifactRequestData(String artifactId,
                                String groupId,
                                String version,
                                String classifier,
                                String extension,
                                List<RepositoryData> repositories) {
      this.artifactId = artifactId;
      this.groupId = groupId;
      this.version = version;
      this.classifier = classifier;
      this.extension = extension;
      this.repositories = repositories;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ArtifactRequestData data = (ArtifactRequestData)o;

      if (!Objects.equals(artifactId, data.artifactId)) return false;
      if (!Objects.equals(groupId, data.groupId)) return false;
      if (!Objects.equals(version, data.version)) return false;
      if (!Objects.equals(classifier, data.classifier)) return false;
      if (!Objects.equals(extension, data.extension)) return false;
      if (!Objects.equals(repositories, data.repositories)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = artifactId != null ? artifactId.hashCode() : 0;
      result = 31 * result + (groupId != null ? groupId.hashCode() : 0);
      result = 31 * result + (version != null ? version.hashCode() : 0);
      result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
      result = 31 * result + (extension != null ? extension.hashCode() : 0);
      result = 31 * result + (repositories != null ? repositories.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "ArtifactRequestData{" +
             "groupId='" + groupId + '\'' +
             ", artifactId='" + artifactId + '\'' +
             ", version='" + version + '\'' +
             '}';
    }
  }

  private static class RepositoryData {
    private final String id;
    private final String url;

    private RepositoryData(String id, String url) {
      this.id = id;
      this.url = url;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RepositoryData data = (RepositoryData)o;

      if (!Objects.equals(id, data.id)) return false;
      if (!Objects.equals(url, data.url)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = id != null ? id.hashCode() : 0;
      result = 31 * result + (url != null ? url.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "RepositoryData{" +
             "id='" + id + '\'' +
             ", url='" + url + '\'' +
             '}';
    }
  }

  private static class ArtifactResultData {
    private final Artifact artifact;
    private final ArtifactRepository repository;
    private final List<Exception> exceptions;

    private ArtifactResultData(Artifact artifact, ArtifactRepository repository, List<Exception> exceptions) {
      this.artifact = artifact;
      this.repository = repository;
      this.exceptions = exceptions;
    }

    private Artifact getArtifact() {
      return artifact;
    }

    private ArtifactRepository getRepository() {
      return repository;
    }

    private List<Exception> getExceptions() {
      return exceptions;
    }

    @Override
    public String toString() {
      return "ArtifactResultData{" +
             "groupId='" + artifact.getGroupId() + '\'' +
             ", artifactId='" + artifact.getArtifactId() + '\'' +
             ", version='" + artifact.getVersion() + '\'' +
             ", repositoryId=" + repository.getId() +
             '}';
    }
  }
}
