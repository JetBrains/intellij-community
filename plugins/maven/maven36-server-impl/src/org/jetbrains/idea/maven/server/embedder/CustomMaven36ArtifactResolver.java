// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.ParallelRunnerForServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomMaven36ArtifactResolver implements ArtifactResolver, Resetable {
  private final ArtifactResolver myWrapee;
  private final Map<ArtifactRequestData, ArtifactResultData> artifactCache = new ConcurrentHashMap<>();

  public CustomMaven36ArtifactResolver(ArtifactResolver wrapee) {
    myWrapee = wrapee;
  }

  protected ArtifactResultData doResolveArtifactAndWrapException(RepositorySystemSession session, ArtifactRequest request) {
    try {
      return new ArtifactResultData(myWrapee.resolveArtifact(session, request), null);
    }
    catch (ArtifactResolutionException e) {
      return new ArtifactResultData(e.getResult(), e);
    }
  }

  private static @NotNull ArtifactResult getResult(ArtifactRequest request, ArtifactResultData resultData) {
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

  @Override
  public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request) throws ArtifactResolutionException {
    if (null == request) return null;

    ArtifactRequestData requestData = new ArtifactRequestData(request);

    ArtifactResultData resultData = artifactCache.computeIfAbsent(requestData, rd -> doResolveArtifactAndWrapException(session, request));

    if (null != resultData.getResolutionException()) {
      throw resultData.getResolutionException();
    }

    return getResult(request, resultData);
  }

  @Override
  public List<ArtifactResult> resolveArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)
    throws ArtifactResolutionException {
    if (null == requests) return null;

    List<ArtifactRequest> requestList = new ArrayList<>(requests);

    List<ArtifactResultData> resultDataList = ParallelRunnerForServer.execute(
      true,
      requests,
      request -> doResolveArtifactAndWrapException(session, request)
    );

    List<ArtifactResult> results = new ArrayList<>(requests.size());
    boolean error = false;
    for (int i = 0; i < requests.size(); i++) {
      ArtifactResultData resultData = resultDataList.get(i);
      if (null != resultData.getResolutionException()) {
        error = true;
      }
      results.add(getResult(requestList.get(i), resultData));
    }

    if (error) {
      throw new ArtifactResolutionException(results);
    }
    return results;
  }

  @Override
  public void reset() {
    artifactCache.clear();
  }
}
