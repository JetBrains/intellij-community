// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Collection;

public abstract class MavenServerConnector implements @NotNull Disposable {
  public static final Logger LOG = Logger.getInstance(MavenServerConnector.class);

  protected final Project myProject;
  protected final MavenServerManager myManager;
  protected @NotNull final MavenDistribution myDistribution;
  protected final Sdk myJdk;
  protected final String myMultimoduleDirectory;

  protected final String myVmOptions;

  public MavenServerConnector(@NotNull Project project,
                              @NotNull MavenServerManager manager,
                              @NotNull Sdk jdk,
                              @NotNull String vmOptions,
                              @NotNull MavenDistribution mavenDistribution,
                              @NotNull String multimoduleDirectory) {
    myProject = project;
    myManager = manager;
    myDistribution = mavenDistribution;
    myVmOptions = vmOptions;
    myJdk = jdk;
    myMultimoduleDirectory = multimoduleDirectory;
  }

  abstract boolean isNew();

  public abstract boolean isCompatibleWith(Sdk jdk, String vmOptions, MavenDistribution distribution);

  protected abstract void connect();

  @NotNull
  protected abstract MavenServer getServer();

  MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings) throws RemoteException {
    return getServer().createEmbedder(settings, MavenRemoteObjectWrapper.ourToken);
  }

  MavenServerIndexer createIndexer() throws RemoteException {
    return getServer().createIndexer(MavenRemoteObjectWrapper.ourToken);
  }

  @NotNull
  public MavenModel interpolateAndAlignModel(final MavenModel model, final File basedir) {
    return perform(() -> {
      MavenModel m = getServer().interpolateAndAlignModel(model, basedir, MavenRemoteObjectWrapper.ourToken);
      RemotePathTransformerFactory.Transformer transformer = RemotePathTransformerFactory.createForProject(myProject);
      if (transformer != RemotePathTransformerFactory.Transformer.ID) {
        new MavenBuildPathsChange((String s) -> transformer.toIdePath(s)).perform(m);
      }
      return m;
    });
  }

  public MavenModel assembleInheritance(final MavenModel model, final MavenModel parentModel) {
    return perform(() -> getServer().assembleInheritance(model, parentModel, MavenRemoteObjectWrapper.ourToken));
  }

  public ProfileApplicationResult applyProfiles(final MavenModel model,
                                                final File basedir,
                                                final MavenExplicitProfiles explicitProfiles,
                                                final Collection<String> alwaysOnProfiles) {
    return perform(
      () -> getServer().applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles, MavenRemoteObjectWrapper.ourToken));
  }


  public abstract void addDownloadListener(MavenServerDownloadListener listener);

  public abstract void removeDownloadListener(MavenServerDownloadListener listener);

  @ApiStatus.Internal
  public void shutdown(boolean wait) {
    myManager.unregisterConnector(this);
  }

  protected <R, E extends Exception> R perform(RemoteObjectWrapper.Retriable<R, E> r) throws E {
    try {
      return r.execute();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public abstract String getSupportType();

  public abstract State getState();

  public enum State {
    STARTING,
    RUNNING,
    FAILED,
    STOPPED
  }

  @Override
  public void dispose() {
    shutdown(true);
  }

  @NotNull
  public Sdk getJdk() {
    return myJdk;
  }

  public MavenDistribution getMavenDistribution() {
    return myDistribution;
  }

  public String getVMOptions() {
    return myVmOptions;
  }

  public Project getProject() {
    return myProject;
  }

  public String getMultimoduleDirectory() {
    return myMultimoduleDirectory;
  }
}
