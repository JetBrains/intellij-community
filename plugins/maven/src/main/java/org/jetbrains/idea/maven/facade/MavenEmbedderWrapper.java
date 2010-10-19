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
package org.jetbrains.idea.maven.facade;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class MavenEmbedderWrapper extends RemoteObjectWrapper<MavenFacadeEmbedder> {
  private MavenFacadeConsole myConsole;
  private MavenFacadeProgressIndicator myIndicator;

  public MavenEmbedderWrapper(@Nullable RemoteObjectWrapper<?> parent) {
    super(parent);
  }

  public void customizeForResolve(MavenConsole console, MavenProgressIndicator indicator) {
    resetConsole(MavenFacadeManager.wrapAndExport(console));
    resetIndicator(MavenFacadeManager.wrapAndExport(indicator));

    perform(new Retriable<Object>() {
      @Override
      public Object execute() throws RemoteException {
        getOrCreateWrappee().customizeForResolve(myConsole, myIndicator);
        return null;
      }
    });
  }

  public void customizeForResolve(final Map<MavenId, File> projectIdToFileMap, MavenConsole console, MavenProgressIndicator indicator) {
    resetConsole(MavenFacadeManager.wrapAndExport(console));
    resetIndicator(MavenFacadeManager.wrapAndExport(indicator));

    perform(new Retriable<Object>() {
      @Override
      public Object execute() throws RemoteException {
        getOrCreateWrappee().customizeForResolve(projectIdToFileMap, myConsole, myIndicator);
        return null;
      }
    });
  }

  public void customizeForStrictResolve(final Map<MavenId, File> projectIdToFileMap,
                                        MavenConsole console,
                                        MavenProgressIndicator indicator) {
    resetConsole(MavenFacadeManager.wrapAndExport(console));
    resetIndicator(MavenFacadeManager.wrapAndExport(indicator));

    perform(new Retriable<Object>() {
      @Override
      public Object execute() throws RemoteException {
        getOrCreateWrappee().customizeForStrictResolve(projectIdToFileMap, myConsole, myIndicator);
        return null;
      }
    });
  }

  @NotNull
  public MavenWrapperExecutionResult resolveProject(@NotNull final VirtualFile file,
                                                    @NotNull final Collection<String> activeProfiles) throws MavenProcessCanceledException {
    return perform(new RetriableCancelable<MavenWrapperExecutionResult>() {
      @Override
      public MavenWrapperExecutionResult execute() throws RemoteException, MavenFacadeProcessCanceledException {
        return getOrCreateWrappee().resolveProject(new File(file.getPath()), activeProfiles);
      }
    });
  }

  @NotNull
  public MavenArtifact resolve(@NotNull final MavenArtifactInfo info,
                               @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    return perform(new RetriableCancelable<MavenArtifact>() {
      @Override
      public MavenArtifact execute() throws RemoteException, MavenFacadeProcessCanceledException {
        return getOrCreateWrappee().resolve(info, remoteRepositories);
      }
    });
  }

  @NotNull
  public List<MavenArtifact> resolveTransitively(
    @NotNull final List<MavenArtifactInfo> artifacts,
    @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {

    return perform(new RetriableCancelable<List<MavenArtifact>>() {
      @Override
      public List<MavenArtifact> execute() throws RemoteException, MavenFacadeProcessCanceledException {
        return getOrCreateWrappee().resolveTransitively(artifacts, remoteRepositories);
      }
    });
  }

  public Collection<MavenArtifact> resolvePlugin(@NotNull final MavenPlugin plugin,
                                                 @NotNull final List<MavenRemoteRepository> repositories,
                                                 @NotNull final NativeMavenProjectHolder nativeMavenProject,
                                                 final boolean transitive) throws MavenProcessCanceledException {
    int id;
    try {
      id = nativeMavenProject.getId();
    }
    catch (RemoteException e) {
      // do not call handleRemoteError here since this error occurred because of previous remote error
      return Collections.emptyList();
    }

    try {
      return getOrCreateWrappee().resolvePlugin(plugin, repositories, id, transitive);
    }
    catch (RemoteException e) {
      // do not try to reconnect here since we have lost NativeMavenProjectHolder anyway.
      handleRemoteError(e);
      return Collections.emptyList();
    }
    catch (MavenFacadeProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
  }

  @NotNull
  public MavenWrapperExecutionResult execute(@NotNull final VirtualFile file,
                                             @NotNull final Collection<String> activeProfiles,
                                             @NotNull final List<String> goals) throws MavenProcessCanceledException {
    return perform(new RetriableCancelable<MavenWrapperExecutionResult>() {
      @Override
      public MavenWrapperExecutionResult execute() throws RemoteException, MavenFacadeProcessCanceledException {
        return getOrCreateWrappee().execute(new File(file.getPath()), activeProfiles, goals);
      }
    });
  }

  public void reset() {
    MavenFacadeEmbedder w = getWrappee();
    if (w == null) return;
    try {
      w.reset();
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
    resetConsole(null);
    resetIndicator(null);
  }

  public void release() {
    MavenFacadeEmbedder w = getWrappee();
    if (w == null) return;
    try {
      w.release();
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
    resetConsole(null);
    resetIndicator(null);
  }

  private void resetConsole(MavenFacadeConsole console) {
    if (myConsole != null) {
      try {
        UnicastRemoteObject.unexportObject(myConsole, true);
      }
      catch (NoSuchObjectException e) {
        MavenLog.LOG.warn(e);
      }
    }
    myConsole = console;
  }

  private void resetIndicator(MavenFacadeProgressIndicator indicator) {
    if (myIndicator != null) {
      try {
        UnicastRemoteObject.unexportObject(myIndicator, true);
      }
      catch (NoSuchObjectException e) {
        MavenLog.LOG.warn(e);
      }
    }
    myIndicator = indicator;
  }

  public void clearCaches() {
    MavenFacadeEmbedder w = getWrappee();
    if (w == null) return;
    try {
      w.clearCaches();
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  public void clearCachesFor(MavenId projectId) {
    MavenFacadeEmbedder w = getWrappee();
    if (w == null) return;
    try {
      w.clearCachesFor(projectId);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }
}
