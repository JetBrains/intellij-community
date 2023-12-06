// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;

import java.io.File;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractMavenServerConnector implements MavenServerConnector {

  public static final Logger LOG = Logger.getInstance(AbstractMavenServerConnector.class);

  protected final Project myProject;
  protected @NotNull final MavenDistribution myDistribution;
  protected final Sdk myJdk;
  protected final Set<String> myMultimoduleDirectories = ConcurrentHashMap.newKeySet();
  private final Object embedderLock = new Object();
  private final Exception myCreationTrace = new Exception();

  protected final String myVmOptions;

  public AbstractMavenServerConnector(@Nullable Project project, // to be removed in future
                                      @NotNull Sdk jdk,
                                      @NotNull String vmOptions,
                                      @NotNull MavenDistribution mavenDistribution,
                                      @NotNull String multimoduleDirectory) {
    myProject = project;
    myDistribution = mavenDistribution;
    myVmOptions = vmOptions;
    myJdk = jdk;
    myMultimoduleDirectories.add(multimoduleDirectory);
  }

  @Override
  public boolean addMultimoduleDir(String multimoduleDirectory) {
    return myMultimoduleDirectories.add(multimoduleDirectory);
  }

  @NotNull
  protected abstract MavenServer getServer();

  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings) throws RemoteException {
    synchronized (embedderLock) {
      try {
        return getServer().createEmbedder(settings, MavenRemoteObjectWrapper.ourToken);
      }
      catch (Exception e) {
        MavenCoreInitializationException cause = ExceptionUtil.findCause(e, MavenCoreInitializationException.class);
        if (cause != null) {
          return new MisconfiguredPlexusDummyEmbedder(myProject, cause.getMessage(),
                                                      myMultimoduleDirectories,
                                                      getMavenDistribution().getVersion(),
                                                      cause.getUnresolvedExtensionId());
        }
        throw e;
      }
    }
  }

  @Override
  public MavenServerIndexer createIndexer() throws RemoteException {
    synchronized (embedderLock) {
      return getServer().createIndexer(MavenRemoteObjectWrapper.ourToken);
    }
  }

  @Override
  @NotNull
  public MavenModel interpolateAndAlignModel(@NotNull MavenModel model, @NotNull Path basedir, @NotNull Path pomDir) {
    return perform(() -> {
      RemotePathTransformerFactory.Transformer transformer = RemotePathTransformerFactory.createForProject(myProject);
      File targetBasedir = new File(transformer.toRemotePathOrSelf(basedir.toString()));
      File targetPomDir = new File(transformer.toRemotePathOrSelf(pomDir.toString()));
      MavenModel m = getServer().interpolateAndAlignModel(model, targetBasedir, targetPomDir, MavenRemoteObjectWrapper.ourToken);
      if (transformer != RemotePathTransformerFactory.Transformer.ID) {
        new MavenBuildPathsChange((String s) -> transformer.toIdePath(s), s -> transformer.canBeRemotePath(s)).perform(m);
      }
      return m;
    });
  }

  @Override
  public MavenModel assembleInheritance(final MavenModel model, final MavenModel parentModel) {
    return perform(() -> getServer().assembleInheritance(model, parentModel, MavenRemoteObjectWrapper.ourToken));
  }

  @Override
  public ProfileApplicationResult applyProfiles(final MavenModel model,
                                                final Path basedir,
                                                final MavenExplicitProfiles explicitProfiles,
                                                final Collection<String> alwaysOnProfiles) {
    return perform(
      () -> {
        RemotePathTransformerFactory.Transformer transformer = RemotePathTransformerFactory.createForProject(myProject);
        File targetBasedir = new File(transformer.toRemotePathOrSelf(basedir.toString()));
        return getServer().applyProfiles(model, targetBasedir, explicitProfiles, new HashSet<>(alwaysOnProfiles),
                                         MavenRemoteObjectWrapper.ourToken);
      });
  }

  protected abstract <R, E extends Exception> R perform(Retriable<R, E> r) throws E;

  @Override
  public void dispose() {
    MavenServerManager.getInstance().shutdownConnector(this, true);
  }

  @Override
  @NotNull
  public Sdk getJdk() {
    return myJdk;
  }


  @Override
  @NotNull
  public MavenDistribution getMavenDistribution() {
    return myDistribution;
  }

  @Override
  public String getVMOptions() {
    return myVmOptions;
  }

  @Override
  public @Nullable Project getProject() {
    return myProject;
  }

  @Override
  public List<String> getMultimoduleDirectories() {
    return new ArrayList<>(myMultimoduleDirectories);
  }

  @Override
  public MavenServerStatus getDebugStatus(boolean clean) {
    return perform(() -> {
      return getServer().getDebugStatus(clean);
    });
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
           Integer.toHexString(this.hashCode()) +
           ", myDistribution=" + myDistribution.getMavenHome() +
           ", myJdk=" + myJdk.getName() +
           ", myMultimoduleDirectories=" + myMultimoduleDirectories +
           ", myCreationTrace = " + ExceptionUtil.getThrowableText(myCreationTrace) +
           '}';
  }

  @FunctionalInterface
  public interface Retriable<T, E extends Exception> extends RemoteObjectWrapper.Retriable<T, E> {
  }
}
