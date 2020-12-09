// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.List;

public class MavenServerConnector implements @NotNull Disposable {
  public static final Logger LOG = Logger.getInstance(MavenServerConnector.class);

  private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
  private final RemoteMavenServerDownloadListener
    myDownloadListener = new RemoteMavenServerDownloadListener();

  private final MavenServerManager myManager;
  private final Integer myDebugPort;

  private boolean myLoggerExported;
  private boolean myDownloadListenerExported;
  private final Sdk myJdk;
  private final MavenDistribution myDistribution;
  private final String myVmOptions;
  private int connectedProjects;

  private MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport mySupport;
  private MavenServer myMavenServer;


  public MavenServerConnector(@NotNull MavenServerManager manager,
                              @NotNull Sdk jdk,
                              @NotNull String vmOptions,
                              @Nullable Integer debugPort,
                              @NotNull MavenDistribution mavenDistribution) {

    myManager = manager;
    myDebugPort = debugPort;
    myDistribution = mavenDistribution;
    myVmOptions = vmOptions;
    myJdk = jdk;
  }

  public boolean isCompatibleWith(Sdk jdk, String vmOptions, MavenDistribution distribution) {
    if (!myDistribution.compatibleWith(distribution)) {
      return false;
    }
    if (!StringUtil.equals(myJdk.getName(), jdk.getName())) {
      return false;
    }
    return StringUtil.equals(vmOptions, myVmOptions);
  }

  void connect(Project project) {
    if (mySupport != null || myMavenServer != null) {
      connectedProjects += 1;
      return;
    }
    try {
      if (myDebugPort != null) {
        //simple connection using JavaDebuggerConsoleFilterProvider
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Listening for transport dt_socket at address: " + myDebugPort);
      }

      mySupport = getSupportFactory(project).create(myJdk, myVmOptions, myDistribution, project, myDebugPort);

      myMavenServer = mySupport.acquire(this, "");
      myLoggerExported = MavenRemoteObjectWrapper.doWrapAndExport(myLogger) != null;
      if (!myLoggerExported) throw new RemoteException("Cannot export logger object");

      myDownloadListenerExported = MavenRemoteObjectWrapper.doWrapAndExport(myDownloadListener) != null;
      if (!myDownloadListenerExported) throw new RemoteException("Cannot export download listener object");

      myMavenServer.set(myLogger, myDownloadListener, MavenRemoteObjectWrapper.ourToken);
    }
    catch (Exception e) {
      if (mySupport != null) {
        try {
          shutdown(false);
        }
        catch (Throwable ignored) {
        }
      }
      cleanUp();
      myManager.cleanUp(this);
      throw new CannotStartServerException(e);
    }
  }

  @NotNull
  private static MavenRemoteProcessSupportFactory getSupportFactory(Project project) {
    MavenRemoteProcessSupportFactory[] factories = MavenRemoteProcessSupportFactory.MAVEN_SERVER_SUPPORT_EP_NAME.getExtensions();
    List<MavenRemoteProcessSupportFactory> aFactories = ContainerUtil.filter(factories, factory -> factory.isApplicable(project));
    if (aFactories.isEmpty()) {
      return new LocalMavenRemoteProcessSupportFactory();
    }
    if (aFactories.size() > 1) {
      LOG.warn("More than one MavenRemoteProcessSupportFactory is applicable: " + aFactories);
    }
    return aFactories.get(0);
  }

  private void cleanUp() {
    if (myLoggerExported) {
      try {
        UnicastRemoteObject.unexportObject(myLogger, true);
      }
      catch (RemoteException e) {
        MavenLog.LOG.warn(e);
      }
      myLoggerExported = false;
    }
    if (myDownloadListenerExported) {
      try {
        UnicastRemoteObject.unexportObject(myDownloadListener, true);
      }
      catch (RemoteException e) {
        MavenLog.LOG.warn(e);
      }
      myDownloadListenerExported = false;
    }
    myMavenServer = null;
    mySupport = null;
  }

  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings) throws RemoteException {
    return myMavenServer.createEmbedder(settings, MavenRemoteObjectWrapper.ourToken);
  }

  public MavenServerIndexer createIndexer() throws RemoteException {
    return myMavenServer.createIndexer(MavenRemoteObjectWrapper.ourToken);
  }


  public void addDownloadListener(MavenServerDownloadListener listener) {
    myDownloadListener.myListeners.add(listener);
  }

  public void removeDownloadListener(MavenServerDownloadListener listener) {
    myDownloadListener.myListeners.remove(listener);
  }

  @NotNull
  public MavenModel interpolateAndAlignModel(final MavenModel model, final File basedir) {
    return perform(() -> {
      MavenModel m = myMavenServer.interpolateAndAlignModel(model, basedir, MavenRemoteObjectWrapper.ourToken);
      RemotePathTransformerFactory.Transformer transformer = RemotePathTransformerFactory.createForProject(basedir.getPath());
      if (transformer != RemotePathTransformerFactory.Transformer.ID) {
        new MavenBuildPathsChange((String s) -> transformer.toIdePath(s)).perform(m);
      }
      return m;
    });
  }

  public MavenModel assembleInheritance(final MavenModel model, final MavenModel parentModel) {
    return perform(() -> myMavenServer.assembleInheritance(model, parentModel, MavenRemoteObjectWrapper.ourToken));
  }

  public ProfileApplicationResult applyProfiles(final MavenModel model,
                                                final File basedir,
                                                final MavenExplicitProfiles explicitProfiles,
                                                final Collection<String> alwaysOnProfiles) {
    return perform(
      () -> myMavenServer.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles, MavenRemoteObjectWrapper.ourToken));
  }


  void shutdown(boolean wait) {
    if (connectedProjects-- > 0) {
      return;
    }
    shutdownForce(wait);
  }


  @ApiStatus.Internal
  void shutdownForce(boolean wait) {
    myManager.unregisterConnector(this);
    if (mySupport != null) {
      mySupport.stopAll(wait);
    }
    cleanUp();
  }

  protected <R, E extends Exception> R perform(RemoteObjectWrapper.Retriable<R, E> r) throws E {
    RemoteException last = null;
    for (int i = 0; i < 2; i++) {
      try {
        return r.execute();
      }
      catch (RemoteException e) {
        last = e;
      }
    }
    cleanUp();
    myManager.cleanUp(this);
    throw new RuntimeException("Cannot reconnect.", last);
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


  private static class RemoteMavenServerLogger extends MavenRemoteObject implements MavenServerLogger {
    @Override
    public void info(Throwable e) {
      MavenLog.LOG.info(e);
    }

    @Override
    public void warn(Throwable e) {
      MavenLog.LOG.warn(e);
    }

    @Override
    public void error(Throwable e) {
      MavenLog.LOG.error(e);
    }

    @Override
    public void print(String s) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(s);
    }
  }

  private static class RemoteMavenServerDownloadListener extends MavenRemoteObject implements MavenServerDownloadListener {
    private final List<MavenServerDownloadListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    @Override
    public void artifactDownloaded(File file, String relativePath) throws RemoteException {
      for (MavenServerDownloadListener each : myListeners) {
        each.artifactDownloaded(file, relativePath);
      }
    }
  }
}
