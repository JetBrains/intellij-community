// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.maven.server.m40.utils;

import com.intellij.util.ExceptionUtilRt;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator;

import javax.inject.Named;
import javax.inject.Singleton;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Named("Intellij Idea Maven 4 Importer Spy")
@Singleton
// consider using Maven40TransferListenerAdapter bound to a repository session instead of global event spy
public class Maven40ImporterSpy extends AbstractEventSpy {

  private volatile MavenServerConsoleIndicator myIndicator;
  private final Set<String> downloadedArtifacts = Collections.newSetFromMap(new ConcurrentHashMap<>());

  @Override
  public void onEvent(Object o) throws Exception {
    if (!(o instanceof RepositoryEvent)) return;
    RepositoryEvent event = (RepositoryEvent)o;
    if (event.getArtifact() == null) {
      return;
    }

    MavenServerConsoleIndicator indicator = myIndicator;
    if (indicator == null) {
      return;
    }

    if (event.getType() == RepositoryEvent.EventType.ARTIFACT_DOWNLOADING) {
      String dependencyId = toString(event.getArtifact());
      indicator.startedDownload(MavenServerConsoleIndicator.ResolveType.DEPENDENCY, dependencyId);
      downloadedArtifacts.add(dependencyId);
    }
    else if (event.getType() == RepositoryEvent.EventType.ARTIFACT_RESOLVED) {
      String dependencyId = toString(event.getArtifact());
      if (downloadedArtifacts.remove(dependencyId)) {
        processResolvedArtifact(event, indicator, dependencyId);
      }
    }
  }

  private static void processResolvedArtifact(RepositoryEvent event, MavenServerConsoleIndicator indicator, String dependencyId)
    throws RemoteException {
    if (event.getExceptions() != null && !event.getExceptions().isEmpty()) {
      StringBuilder builder = new StringBuilder();
      for (Exception e : event.getExceptions()) {
        String stackTrace = ExceptionUtilRt.getThrowableText(e, "com.jetbrains");
        builder.append(stackTrace).append("\n");
      }
      indicator
        .failedDownload(MavenServerConsoleIndicator.ResolveType.DEPENDENCY, dependencyId, event.getException().getMessage(),
                        builder.toString());
    }
    else {
      indicator.completedDownload(MavenServerConsoleIndicator.ResolveType.DEPENDENCY, dependencyId);
    }
  }

  @Override
  public void close() {
    downloadedArtifacts.clear();
  }

  private static String toString(Artifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getClassifier() + ":" + artifact.getVersion();
  }

  public void setIndicator(MavenServerConsoleIndicator indicator) {
    myIndicator = indicator;
  }
}
