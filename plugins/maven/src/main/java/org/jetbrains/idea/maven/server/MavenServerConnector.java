// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.List;

public class MavenServerConnector implements @NotNull Disposable {


  private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
  private final RemoteMavenServerDownloadListener
    myDownloadListener = new RemoteMavenServerDownloadListener();

  private final Project myProject;
  private final MavenServerManager myManager;
  private final Integer myDebugPort;

  private boolean myLoggerExported;
  private boolean myDownloadListenerExported;
  private final Sdk myJdk;
  private final MavenDistribution myDistribution;
  private final String myVmOptions;

  private MavenServerRemoteProcessSupport mySupport;
  private MavenServer myMavenServer;


  public MavenServerConnector(@NotNull Project project,
                              @NotNull MavenServerManager manager,
                              @NotNull MavenWorkspaceSettings settings,
                              @NotNull Sdk jdk,
                              @Nullable Integer debugPort) {

    myProject = project;
    myManager = manager;
    myDebugPort = debugPort;
    myDistribution = findMavenDistribution(project, settings);
    settings.generalSettings.setMavenHome(myDistribution.getMavenHome().getAbsolutePath());
    myVmOptions = readVmOptions(project, settings);
    myJdk = jdk;
    connect();
  }

  public MavenServerConnector(@NotNull Project project,
                              @NotNull MavenServerManager manager,
                              @NotNull MavenWorkspaceSettings settings,
                              @NotNull Sdk jdk) {
    this(project, manager, settings, jdk, null);
  }

  public boolean isSettingsStillValid(MavenWorkspaceSettings settings) {
    String baseDir = myProject.getBasePath();
    if (baseDir == null) { //for default projects and unit tests backward-compatibility
      return true;
    }
    String distributionUrl = MavenWrapperSupport.getWrapperDistributionUrl(LocalFileSystem.getInstance().findFileByPath(baseDir));
    if (distributionUrl != null && !distributionUrl.equals(myDistribution.getName())) { //new maven url in maven-wrapper.properties
      return false;
    }
    String newVmOptions = readVmOptions(myProject, settings);
    return StringUtil.equals(newVmOptions, myVmOptions);
  }

  private static String readVmOptions(Project project, MavenWorkspaceSettings settings) {

    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) return settings.importingSettings.getVmOptionsForImporter();
    VirtualFile mvn = baseDir.findChild(".mvn");
    if (mvn == null) return settings.importingSettings.getVmOptionsForImporter();
    VirtualFile jdkOpts = mvn.findChild("jvm.config");
    if (jdkOpts == null) return settings.importingSettings.getVmOptionsForImporter();
    try {
      return new String(jdkOpts.contentsToByteArray(true), CharsetToolkit.UTF8_CHARSET);
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
      return settings.importingSettings.getVmOptionsForImporter();
    }
  }


  private static @Nullable String getWrapperDistributionUrl(Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      return null;
    }
    return MavenWrapperSupport.getWrapperDistributionUrl(baseDir);
  }

  private static MavenDistribution findMavenDistribution(Project project, MavenWorkspaceSettings settings) {
    MavenSyncConsole console = MavenProjectsManager.getInstance(project).getSyncConsole();
    String distributionUrl = getWrapperDistributionUrl(project);
    if (distributionUrl == null) {
      MavenDistribution distribution = new MavenDistributionConverter().fromString(settings.generalSettings.getMavenHome());
      if (distribution == null) {
        console.addWarning(SyncBundle.message("cannot.resolve.maven.home"), SyncBundle
          .message("is.not.correct.maven.home.reverting.to.embedded", settings.generalSettings.getMavenHome()));
        return MavenServerManager.resolveEmbeddedMavenHome();
      }
      return distribution;
    }
    else {
      try {

        console.startWrapperResolving();
        MavenDistribution distribution = new MavenWrapperSupport().downloadAndInstallMaven(distributionUrl);
        console.finishWrapperResolving(null);
        return distribution;
      }
      catch (RuntimeException | IOException e) {
        MavenLog.LOG.info(e);
        console.finishWrapperResolving(e);
        return MavenServerManager.resolveEmbeddedMavenHome();
      }
    }
  }

  private void connect() {
    if (mySupport != null || myMavenServer != null) {
      throw new IllegalStateException("Already connected");
    }
    try {
      if (myDebugPort != null) {
        //simple connection using JavaDebuggerConsoleFilterProvider
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Listening for transport dt_socket at address: " + myDebugPort);
      }

      mySupport = new MavenServerRemoteProcessSupport(myJdk, myVmOptions, myDistribution, myProject, myDebugPort);
      myMavenServer = mySupport.acquire(this, "");
      myLoggerExported = MavenRemoteObjectWrapper.doWrapAndExport(myLogger) != null;
      if (!myLoggerExported) throw new RemoteException("Cannot export logger object");

      myDownloadListenerExported = MavenRemoteObjectWrapper.doWrapAndExport(myDownloadListener) != null;
      if (!myDownloadListenerExported) throw new RemoteException("Cannot export download listener object");

      myMavenServer.set(myLogger, myDownloadListener, MavenRemoteObjectWrapper.ourToken);
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot start maven service", e);
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
    return perform(() -> myMavenServer.interpolateAndAlignModel(model, basedir, MavenRemoteObjectWrapper.ourToken));
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

  public void shutdown(boolean wait) {
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
        MavenServerRemoteProcessSupport processSupport = mySupport;
        if (processSupport != null) {
          processSupport.stopAll(false);
        }
        cleanUp();
        connect();
      }
    }
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
