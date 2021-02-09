// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MavenServerConnector implements @NotNull Disposable {
  public static final Logger LOG = Logger.getInstance(MavenServerConnector.class);
  private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
  private final RemoteMavenServerDownloadListener
    myDownloadListener = new RemoteMavenServerDownloadListener();

  private final Project myProject;
  private final MavenServerManager myManager;
  private final Integer myDebugPort;

  private boolean myLoggerExported;
  private boolean myDownloadListenerExported;
  private final Sdk myJdk;
  private final String myMultimoduleDirectory;
  private @NotNull final MavenDistribution myDistribution;
  private final String myVmOptions;
  private AtomicBoolean myConnectStarted = new AtomicBoolean(false);

  private MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport mySupport;
  private final AsyncPromise<MavenServer> myServerPromise = new AsyncPromise<>();


  public MavenServerConnector(@NotNull Project project,
                              @NotNull MavenServerManager manager,
                              @NotNull Sdk jdk,
                              @NotNull String vmOptions,
                              @Nullable Integer debugPort,
                              @NotNull MavenDistribution mavenDistribution,
                              @NotNull String multimoduleDirectory) {
    myProject = project;
    myManager = manager;
    myDebugPort = debugPort;
    myDistribution = mavenDistribution;
    myVmOptions = vmOptions;
    myJdk = jdk;
    myMultimoduleDirectory = multimoduleDirectory;
    myServerPromise.onError(e -> {
    }); //Promise w/o error handler sends all exceptions to analyzer, we don't need them

  }

  boolean isNew(){
    return !myConnectStarted.get();
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
  //@SuppressWarnings("SSBasedInspection") //need tests refactoring
  void connect() {
    if(!myConnectStarted.compareAndSet(false, true)){
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(new StartServerTask());
  }

  @NotNull
  private MavenServer getServer() {
    try {
        while (!myServerPromise.isDone()) {
          try {
            return myServerPromise.get(100, TimeUnit.MILLISECONDS);
          } catch (Exception ignore){}
          if (myProject.isDisposed()) {
            throw new CannotStartServerException("Project already disposed");
          }
          ProgressManager.checkCanceled();
        }
        return myServerPromise.get();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      try {
        shutdown(false);
      }
      catch (Throwable ignored) {
      }
      cleanUp();
      myManager.cleanUp(this);
      throw new CannotStartServerException(e);
    }
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

  }

  MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings) throws RemoteException {
    return getServer().createEmbedder(settings, MavenRemoteObjectWrapper.ourToken);
  }

  MavenServerIndexer createIndexer() throws RemoteException {
    return getServer().createIndexer(MavenRemoteObjectWrapper.ourToken);
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


  public void shutdown(boolean wait) {
    shutdownForce(wait);
  }


  @ApiStatus.Internal
  void shutdownForce(boolean wait) {
    myManager.unregisterConnector(this);
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    if (support != null) {
      support.stopAll(wait);
      mySupport = null;
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

  public Project getProject() {
    return myProject;
  }

  public String getMultimoduleDirectory() {
    return myMultimoduleDirectory;
  }

  public String getSupportType() {
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    return support == null? "???" : support.type();
  }

  public State getState() {
    switch (myServerPromise.getState()){
      case SUCCEEDED: {
        return mySupport == null? State.STOPPED : State.RUNNING;
      }
      case REJECTED: return State.FAILED;
      default: return State.STARTING;
    }
  }

  public enum State {
    STARTING,
    RUNNING,
    FAILED,
    STOPPED
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

  private class StartServerTask implements Runnable {
    @Override
    public void run() {
      ProgressIndicator indicator = new EmptyProgressIndicator();
      MavenLog.LOG.info("Connecting maven connector in " + myMultimoduleDirectory);
      try {
        if (myDebugPort != null) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Listening for transport dt_socket at address: " + myDebugPort);
        }
        MavenRemoteProcessSupportFactory factory = MavenRemoteProcessSupportFactory.forProject(myProject);
        mySupport = factory.create(myJdk, myVmOptions, myDistribution, myProject, myDebugPort);
        MavenServer server = mySupport.acquire(this, "", indicator);
        myLoggerExported = MavenRemoteObjectWrapper.doWrapAndExport(myLogger) != null;

        if (!myLoggerExported) throw new RemoteException("Cannot export logger object");

        myDownloadListenerExported = MavenRemoteObjectWrapper.doWrapAndExport(myDownloadListener) != null;
        if (!myDownloadListenerExported) throw new RemoteException("Cannot export download listener object");

        server.set(myLogger, myDownloadListener, MavenRemoteObjectWrapper.ourToken);
        myServerPromise.setResult(server);
        MavenLog.LOG.info("Connector in " + myMultimoduleDirectory  + " has been connected");
      }
      catch (Throwable e) {
        MavenLog.LOG.warn("Cannot connect connector in " + myMultimoduleDirectory, e);
        myServerPromise.setError(e);
      }
    }
  }
}
