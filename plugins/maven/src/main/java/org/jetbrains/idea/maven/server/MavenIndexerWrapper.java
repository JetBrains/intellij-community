// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenIndexId;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public abstract class MavenIndexerWrapper extends MavenRemoteObjectWrapper<MavenServerIndexer> {
  private final Project myProject;

  public MavenIndexerWrapper(@Nullable RemoteObjectWrapper<?> parent, Project project) {
    super(parent);
    myProject = project;
  }

  public void releaseIndex(MavenIndexId mavenIndexId) throws MavenServerIndexerException {
    MavenLog.LOG.debug("releaseIndex " + mavenIndexId.indexId);

    MavenServerIndexer w = getWrappee();
    if (w == null) return;

    try {
      w.releaseIndex(mavenIndexId, ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  public boolean indexExists(File dir) {
    try {
      return getOrCreateWrappee().indexExists(dir, ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
    return false;
  }

  public int getIndexCount() {
    return perform(() -> getOrCreateWrappee().getIndexCount(ourToken));
  }

  public void updateIndex(@NotNull final MavenIndexId mavenIndexId,
                          @Nullable final MavenGeneralSettings settings,
                          @NotNull final MavenProgressIndicator indicator) throws MavenProcessCanceledException,
                                                                         MavenServerIndexerException {
    performCancelable(() -> {
      MavenServerProgressIndicator indicatorWrapper = wrapAndExport(indicator);
      try {
        getOrCreateWrappee().updateIndex(mavenIndexId, MavenServerManager.convertSettings(myProject, settings), indicatorWrapper, ourToken);
      }
      finally {
        UnicastRemoteObject.unexportObject(indicatorWrapper, true);
      }
      return null;
    });
  }

  public void processArtifacts(final MavenIndexId mavenIndexId, final MavenIndicesProcessor processor) throws MavenServerIndexerException {
    perform(() -> {
      try {
        int start = 0;
        List<IndexedMavenId> list;
        do {
          list = getOrCreateWrappee().processArtifacts(mavenIndexId, start, ourToken);
          if (list != null) {
            processor.processArtifacts(list);
            start += list.size();
          }
        }
        while (list != null);
        return null;
      } catch (Exception e){
        MavenLog.LOG.error("maven index id " + mavenIndexId, e);
        return null;
      }
    });
  }

  @Nullable
  public IndexedMavenId addArtifact(final MavenIndexId mavenIndexId, final File artifactFile) throws MavenServerIndexerException {
    return perform(() -> {
      try {
        return getOrCreateWrappee().addArtifact(mavenIndexId, artifactFile, ourToken);
      }
      catch (Throwable ignore) {
        return null;
      }
    });
  }

  public Set<MavenArtifactInfo> search(final MavenIndexId mavenIndexId, final String pattern, final int maxResult)
    throws MavenServerIndexerException {
    return perform(() -> getOrCreateWrappee().search(mavenIndexId, pattern, maxResult, ourToken));
  }

  /**
   * @deprecated use {@link MavenEmbedderWrapper#getArchetypes()}
   */
  @Deprecated
  public Collection<MavenArchetype> getArchetypes() {
    return perform(() -> getOrCreateWrappee().getArchetypes(ourToken));
  }

  @TestOnly
  public void releaseInTests() {
    MavenServerIndexer w = getWrappee();
    if (w == null) return;
    try {
      w.release(ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  private static final class RemoteMavenServerIndicesProcessor extends MavenRemoteObject implements MavenServerIndicesProcessor {
    private final MavenIndicesProcessor myProcessor;

    private RemoteMavenServerIndicesProcessor(MavenIndicesProcessor processor) {
      myProcessor = processor;
    }

    @Override
    public void processArtifacts(Collection<IndexedMavenId> artifacts) {
      myProcessor.processArtifacts(artifacts);
    }
  }

}

