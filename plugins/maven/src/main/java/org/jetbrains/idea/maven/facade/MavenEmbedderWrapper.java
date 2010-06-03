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
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MavenEmbedderWrapper {
  private final MavenFacadeEmbedder myWrappee;

  private MavenFacadeConsole myConsole;
  private MavenFacadeProgressIndicator myIndicator;

  public MavenEmbedderWrapper(MavenFacadeEmbedder wrappee) {
    myWrappee = wrappee;
  }

  public void customizeForResolve(MavenConsole console, MavenProgressIndicator indicator) {
    try {
      resetConsole(MavenFacadeManager.wrapAndExport(console));
      resetIndicator(MavenFacadeManager.wrapAndExport(indicator));

      myWrappee.customizeForResolve(myConsole, myIndicator);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void customizeForResolve(Map<MavenId, File> projectIdToFileMap, MavenConsole console, MavenProgressIndicator indicator) {
    try {
      resetConsole(MavenFacadeManager.wrapAndExport(console));
      resetIndicator(MavenFacadeManager.wrapAndExport(indicator));

      myWrappee.customizeForResolve(projectIdToFileMap, myConsole, myIndicator);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void customizeForStrictResolve(Map<MavenId, File> projectIdToFileMap,
                                        MavenConsole console,
                                        MavenProgressIndicator indicator) {
    try {
      resetConsole(MavenFacadeManager.wrapAndExport(console));
      resetIndicator(MavenFacadeManager.wrapAndExport(indicator));

      myWrappee.customizeForStrictResolve(projectIdToFileMap, myConsole, myIndicator);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public MavenWrapperExecutionResult resolveProject(@NotNull VirtualFile file,
                                                    @NotNull Collection<String> activeProfiles) throws MavenProcessCanceledException {
    try {
      return myWrappee.resolveProject(new File(file.getPath()), activeProfiles);
    }
    catch (MavenFacadeProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public MavenArtifact resolve(@NotNull MavenArtifactInfo info,
                               @NotNull List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    try {
      return myWrappee.resolve(info, remoteRepositories);
    }
    catch (MavenFacadeProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public List<MavenArtifact> resolveTransitively(
    @NotNull List<MavenArtifactInfo> artifacts,
    @NotNull List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    try {
      return myWrappee.resolveTransitively(artifacts, remoteRepositories);
    }
    catch (MavenFacadeProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public Collection<MavenArtifact> resolvePlugin(@NotNull MavenPlugin plugin,
                                                 @NotNull List<MavenRemoteRepository> repositories,
                                                 @NotNull NativeMavenProjectHolder nativeMavenProject,
                                                 boolean transitive) throws MavenProcessCanceledException {
    try {
      return myWrappee.resolvePlugin(plugin, repositories, nativeMavenProject.getId(), transitive);
    }
    catch (MavenFacadeProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public MavenWrapperExecutionResult execute(@NotNull VirtualFile file,
                                             @NotNull Collection<String> activeProfiles,
                                             @NotNull List<String> goals) throws MavenProcessCanceledException {
    try {
      return myWrappee.execute(new File(file.getPath()), activeProfiles, goals);
    }
    catch (MavenFacadeProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void reset() {
    try {
      myWrappee.reset();
      resetConsole(null);
      resetIndicator(null);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  private void resetConsole(MavenFacadeConsole console) throws NoSuchObjectException {
    if (myConsole != null) {
      UnicastRemoteObject.unexportObject(myConsole, true);
    }
    myConsole = console;
  }

  private void resetIndicator(MavenFacadeProgressIndicator indicator) throws NoSuchObjectException {
    if (myIndicator != null) {
      UnicastRemoteObject.unexportObject(myIndicator, true);
    }
    myIndicator = indicator;
  }

  public void release() {
    try {
      myWrappee.release();
      resetConsole(null);
      resetIndicator(null);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void clearCaches() {
    try {
      myWrappee.clearCaches();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void clearCachesFor(MavenId projectId) {
    try {
      myWrappee.clearCachesFor(projectId);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }
}
