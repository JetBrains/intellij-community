// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.util.ExceptionUtilRt;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;

import javax.inject.Named;
import javax.inject.Singleton;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Named("Intellij Idea Maven Importer Spy")
@Singleton
public class MavenImporterSpy extends AbstractEventSpy {

  private volatile MavenServerProgressIndicator myIndicator;
  private Set<String> downloadedArtifacts = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

  @Override
  public void onEvent(Object o) throws Exception {
    if (!(o instanceof RepositoryEvent)) return;
    RepositoryEvent event = (RepositoryEvent)o;
    if (event.getArtifact() == null) {
      return;
    }

    MavenServerProgressIndicator indicator = myIndicator;
    if (indicator == null) {
      return;
    }
    String dependencyId = toString(event.getArtifact());
    if (event.getType() == RepositoryEvent.EventType.ARTIFACT_DOWNLOADING) {
      indicator.startedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, dependencyId);
      downloadedArtifacts.add(dependencyId);
    }
    if (event.getType() == RepositoryEvent.EventType.ARTIFACT_RESOLVED) {
      if (downloadedArtifacts.remove(dependencyId)) {
        processResolvedArtifact(event, indicator, dependencyId);
      }
    }
  }

  private static void processResolvedArtifact(RepositoryEvent event, MavenServerProgressIndicator indicator, String dependencyId)
    throws RemoteException {
    if (event.getExceptions() != null && !event.getExceptions().isEmpty()) {
      StringBuilder builder = new StringBuilder();
      for (Exception e : event.getExceptions()) {
        String stackTrace = ExceptionUtilRt.getThrowableText(e, "com.jetbrains");
        builder.append(stackTrace).append("\n");
      }
      indicator
        .failedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, dependencyId, event.getException().getMessage(),
                        builder.toString());
    }
    else {
      indicator.completedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, dependencyId);
    }
  }

  @Override
  public void close() {
    downloadedArtifacts.clear();
  }

  private static String toString(Artifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
  }

  public void setIndicator(MavenServerProgressIndicator indicator) {
    myIndicator = indicator;
  }
}
