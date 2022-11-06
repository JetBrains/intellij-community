// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.impl.TrustStateListener;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import org.apache.commons.lang.SystemUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.MavenDisposable;
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.indices.MavenIndices;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.MavenWslUtil;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

public final class MavenServerManager implements Disposable {
  public static final String BUNDLED_MAVEN_2 = "Bundled (Maven 2)";
  public static final String BUNDLED_MAVEN_3 = "Bundled (Maven 3)";
  public static final String WRAPPED_MAVEN = "Use Maven wrapper";

  private final Map<String, MavenServerConnector> myMultimoduleDirToConnectorMap = new HashMap<>();

  //TODO: should be replaced by map, where key is the indexing directory. (local/wsl)
  private MavenIndexingConnectorImpl myIndexingConnector = null;
  private MavenIndexerWrapper myIndexerWrapper = null;

  private File eventListenerJar;

  public Collection<MavenServerConnector> getAllConnectors() {
    Set<MavenServerConnector> set = Collections.newSetFromMap(new IdentityHashMap<>());
    synchronized (myMultimoduleDirToConnectorMap) {
      set.addAll(myMultimoduleDirToConnectorMap.values());
      if (myIndexingConnector != null) {
        set.add(myIndexingConnector);
      }
    }
    return set;
  }

  void cleanUp(MavenServerConnector connector) {
    synchronized (myMultimoduleDirToConnectorMap) {
      myMultimoduleDirToConnectorMap.entrySet().removeIf(e -> e.getValue() == connector);
    }
  }

  public static MavenServerManager getInstance() {
    return ApplicationManager.getApplication().getService(MavenServerManager.class);
  }

  @Nullable
  public static MavenServerManager getInstanceIfCreated() {
    return ApplicationManager.getApplication().getServiceIfCreated(MavenServerManager.class);
  }

  public MavenServerManager() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appWillBeClosed(boolean isRestart) {
        ProgressManager.getInstance().run(new Task.Modal(null, RunnerBundle.message("maven.server.shutdown"), false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            shutdown(true);
          }
        });
      }
    });

    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        if (MavenUtil.INTELLIJ_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
          ProgressManager.getInstance().run(new Task.Modal(null, RunnerBundle.message("maven.server.shutdown"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              shutdown(true);
            }
          });
        }
      }
    });

    connection.subscribe(TrustStateListener.TOPIC, new TrustStateListener() {
      @Override
      public void onProjectTrusted(@NotNull Project project) {
        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        if (manager.isMavenizedProject()) {
          MavenUtil.restartMavenConnectors(project, true);
        }
      }

      @Override
      public void onProjectTrustedFromNotification(@NotNull Project project) {
        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        if (manager.isMavenizedProject()) {
          manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
        }
      }
    });
  }

  public MavenServerConnector getConnector(@NotNull Project project, @NotNull String workingDirectory) {
    String multimoduleDirectory = MavenDistributionsCache.getInstance(project).getMultimoduleDirectory(workingDirectory);
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
    Sdk jdk = getJdk(project, settings);

    MavenServerConnector connector = doGetOrCreateConnector(project, multimoduleDirectory, jdk);
    if (connector.isNew()) {
      connector.connect();
    }
    else {
      if (!compatibleParameters(project, connector, jdk, multimoduleDirectory)) {
        MavenLog.LOG.info("[connector] " + connector + " is incompatible, restarting");
        shutdownConnector(connector, false);
        connector = this.doGetOrCreateConnector(project, multimoduleDirectory, jdk);
        connector.connect();
      }
    }
    MavenLog.LOG.debug("[connector] get " + connector);
    return connector;
  }

  private MavenServerConnector doGetOrCreateConnector(@NotNull Project project,
                                                      @NotNull String multimoduleDirectory,
                                                      @NotNull Sdk jdk) {
    MavenServerConnector connector;
    synchronized (myMultimoduleDirToConnectorMap) {
      connector = myMultimoduleDirToConnectorMap.get(multimoduleDirectory);
      if (connector != null) return connector;
      connector = findCompatibleConnector(project, jdk, multimoduleDirectory);
      if (connector != null) {
        MavenLog.LOG.debug("[connector] use existing connector for " + connector);
        connector.addMultimoduleDir(multimoduleDirectory);
      }
      else {
        connector = registerNewConnector(project, jdk, multimoduleDirectory);
      }
      myMultimoduleDirToConnectorMap.put(multimoduleDirectory, connector);
    }

    return connector;
  }

  private @Nullable MavenServerConnector findCompatibleConnector(@NotNull Project project,
                                                                 @NotNull Sdk jdk,
                                                                 @NotNull String multimoduleDirectory) {
    MavenDistribution distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multimoduleDirectory);
    String vmOptions = MavenDistributionsCache.getInstance(project).getVmOptions(multimoduleDirectory);
    for (Map.Entry<String, MavenServerConnector> entry : myMultimoduleDirToConnectorMap.entrySet()) {
      if (!entry.getValue().getProject().equals(project)) continue;
      if (Registry.is("maven.server.per.idea.project")) return entry.getValue();
      if (entry.getValue().isCompatibleWith(jdk, vmOptions, distribution)) {
        return entry.getValue();
      }
    }

    return null;
  }

  private @NotNull MavenServerConnector registerNewConnector(Project project,
                                                             Sdk jdk,
                                                             String multimoduleDirectory) {
    MavenDistribution distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multimoduleDirectory);
    String vmOptions = MavenDistributionsCache.getInstance(project).getVmOptions(multimoduleDirectory);
    Integer debugPort = getFreeDebugPort();
    MavenServerConnector connector;
    if (TrustedProjects.isTrusted(project) || project.isDefault()) {
      connector = new MavenServerConnectorImpl(project, this, jdk, vmOptions, debugPort, distribution, multimoduleDirectory);
      MavenLog.LOG.debug("[connector] new maven connector " + connector);
    }
    else {
      MavenLog.LOG.warn("Project " + project + " not trusted enough. Will not start maven for it");
      connector = new DummyMavenServerConnector(project, this, jdk, vmOptions, distribution, multimoduleDirectory);
    }
    registerDisposable(project, connector);
    return connector;
  }

  private void registerDisposable(Project project, MavenServerConnector connector) {
    Disposer.register(MavenDisposable.getInstance(project), () -> {
      ApplicationManager.getApplication().executeOnPooledThread(() -> shutdownConnector(connector, true));
    });
  }


  private static Integer getFreeDebugPort() {
    if (Registry.is("maven.server.debug")) {
      try {
        return NetUtils.findAvailableSocketPort();
      }
      catch (IOException e) {
        MavenLog.LOG.warn(e);
      }
    }
    return null;
  }

  @NotNull
  private static Sdk getJdk(Project project, MavenWorkspaceSettings settings) {
    String jdkForImporterName = settings.getImportingSettings().getJdkForImporter();
    Sdk jdk;
    try {
      jdk = MavenUtil.getJdk(project, jdkForImporterName);
    }
    catch (ExternalSystemJdkException e) {
      jdk = MavenUtil.getJdk(project, MavenRunnerSettings.USE_PROJECT_JDK);
      MavenProjectsManager.getInstance(project).getSyncConsole().addWarning(SyncBundle.message("importing.jdk.changed"),
                                                                            SyncBundle.message("importing.jdk.changed.description",
                                                                                               jdkForImporterName, jdk.getName())
      );
    }
    if (JavaSdkVersionUtil.isAtLeast(jdk, JavaSdkVersion.JDK_1_8)) {
      return jdk;
    }
    else {
      MavenLog.LOG.info("Selected jdk [" + jdk.getName() + "] is not JDK1.8+ Will use internal jdk instead");
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }
  }

  private static boolean compatibleParameters(Project project,
                                              MavenServerConnector connector,
                                              Sdk jdk,
                                              String multimoduleDirectory) {
    if (Registry.is("maven.server.per.idea.project")) return true;
    MavenDistributionsCache cache = MavenDistributionsCache.getInstance(project);
    MavenDistribution distribution = cache.getMavenDistribution(multimoduleDirectory);
    String vmOptions = cache.getVmOptions(multimoduleDirectory);
    return connector.isCompatibleWith(jdk, vmOptions, distribution);
  }

  @Override
  public void dispose() {
    shutdown(true);
  }


  public boolean shutdownConnector(MavenServerConnector connector, boolean wait) {
    MavenServerConnector connectorToStop = removeConnector(connector);
    if (connectorToStop == null) return false;
    connectorToStop.stop(wait);
    return true;
  }

  private MavenServerConnector removeConnector(@Nullable MavenServerConnector connector) {
    if(connector == null) return null;
    synchronized (myMultimoduleDirToConnectorMap) {
      if (myIndexingConnector == connector) {
        myIndexingConnector = null;
        myIndexerWrapper = null;
        return connector;
      }
      if (!myMultimoduleDirToConnectorMap.values().remove(connector)) {
        return null;
      }
    }
    return connector;
  }

  /**
   * use MavenUtil.restartMavenConnectors
   */
  public void shutdown(boolean wait) {
    Collection<MavenServerConnector> values;
    synchronized (myMultimoduleDirToConnectorMap) {
      values = new ArrayList<>(myMultimoduleDirToConnectorMap.values());
    }


    shutdownConnector(myIndexingConnector, wait);
    values.forEach(c -> shutdownConnector(c, wait));

  }

  public static boolean verifyMavenSdkRequirements(@NotNull Sdk jdk, String mavenVersion) {
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.3.1") < 0) {
      return true;
    }
    SdkTypeId sdkType = jdk.getSdkType();
    if (sdkType instanceof JavaSdk) {
      JavaSdkVersion version = ((JavaSdk)sdkType).getVersion(jdk);
      if (version == null || version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
        return true;
      }
    }
    return false;
  }

  public static File getMavenEventListener() {
    return getInstance().getEventListenerJar();
  }

  private File getEventListenerJar() {
    if (eventListenerJar != null) {
      return eventListenerJar;
    }
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final String root = pluginFileOrDir.getParent();
    if (pluginFileOrDir.isDirectory()) {
      eventListenerJar = getEventSpyPathForLocalBuild();
    }
    else {
      eventListenerJar = new File(root, "maven-event-listener.jar");
    }
    if (!eventListenerJar.exists()) {
      if (ApplicationManager.getApplication().isInternal()) {
        MavenLog.LOG.warn("""
                            Event listener does not exist: Please run rebuild for maven modules:
                            community/plugins/maven/maven-event-listener
                            and all maven*-server* modules"""
        );
      }
      else {
        MavenLog.LOG.warn("Event listener does not exist " + eventListenerJar);
      }
    }
    return eventListenerJar;
  }

  private static File getEventSpyPathForLocalBuild() {
    final File root = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    return new File(root.getParent(), "intellij.maven.server.eventListener");
  }

  public static @Nullable String getMavenVersion(@Nullable String mavenHome) {
    return MavenUtil.getMavenVersion(getMavenHomeFile(mavenHome));
  }

  @Nullable
  public String getMavenVersion(@Nullable File mavenHome) {
    return MavenUtil.getMavenVersion(mavenHome);
  }

  /**
   * @deprecated use {@link MavenGeneralSettings.mavenHome} and {@link MavenUtil.getMavenVersion}
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public String getCurrentMavenVersion() {
    return null;
  }



  @NotNull
  public MavenEmbedderWrapper createEmbedder(final Project project,
                                             final boolean alwaysOnline,
                                             @Nullable String workingDirectory,
                                             @NotNull String multiModuleProjectDirectory) {

    return new MavenEmbedderWrapper(project, null) {
      private MavenServerConnector myConnector;

      @NotNull
      @Override
      protected synchronized MavenServerEmbedder create() throws RemoteException {
        MavenServerSettings settings = convertSettings(project, MavenProjectsManager.getInstance(project).getGeneralSettings());
        if (alwaysOnline && settings.isOffline()) {
          settings = settings.clone();
          settings.setOffline(false);
        }

        RemotePathTransformerFactory.Transformer transformer = RemotePathTransformerFactory.createForProject(project);
        String sdkPath = MavenUtil.getSdkPath(ProjectRootManager.getInstance(project).getProjectSdk());
        if (sdkPath != null) {
          sdkPath = transformer.toRemotePath(sdkPath);
        }
        settings.setProjectJdk(sdkPath);
        myConnector = MavenServerManager.this.getConnector(project, multiModuleProjectDirectory);

        return myConnector.createEmbedder(
          new MavenEmbedderSettings(settings, workingDirectory == null ? null : transformer.toRemotePath(workingDirectory),
                                    transformer.toRemotePath(multiModuleProjectDirectory)));
      }


      @Override
      protected synchronized void cleanup() {
        super.cleanup();
        if (myConnector != null) {
          shutdownConnector(myConnector, false);
        }
      }
    };
  }


  public MavenIndexerWrapper createIndexer(@NotNull Project project) {
    if (Registry.is("maven.dedicated.indexer")) {
      return createDedicatedIndexer();
    }
    else {
      return createLegacyIndexer(project);
    }
  }

  private MavenIndexerWrapper createDedicatedIndexer() {
    if (myIndexerWrapper != null) return myIndexerWrapper;
    synchronized (myMultimoduleDirToConnectorMap) {
      if (myIndexerWrapper != null) return myIndexerWrapper;
      String workingDir = SystemUtils.getUserHome().getAbsolutePath();
      myIndexerWrapper =
        new MavenIndexerWrapper(null) {

          @Override
          protected MavenIndices createMavenIndices() {
            MavenIndices indices = new MavenIndices(this, getIndicesDir().toFile());
            Disposer.register(MavenServerManager.this, indices);
            return indices;
          }

          @Override
          protected @NotNull MavenServerIndexer create() throws RemoteException {
            MavenServerConnector indexingConnector = getIndexingConnector();
            return indexingConnector.createIndexer();
          }

          @Override
          protected synchronized void handleRemoteError(RemoteException e) {
            super.handleRemoteError(e);
            if (waitIfNotIdeaShutdown()) {
              MavenIndexingConnectorImpl indexingConnector = myIndexingConnector;
              if (indexingConnector != null && !indexingConnector.checkConnected()) {
                shutdownConnector(indexingConnector, true);
              }
            }
          }

          private static boolean waitIfNotIdeaShutdown() {
            try {
              Thread.sleep(100);
              return true;
            }
            catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
            return false;
          }

          private MavenServerConnector getIndexingConnector() {
            if (myIndexingConnector != null) return myIndexingConnector;
            synchronized (myMultimoduleDirToConnectorMap) {
              if (myIndexingConnector != null) return myIndexingConnector;
              myIndexingConnector = new MavenIndexingConnectorImpl(MavenServerManager.this,
                                                                   JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk(),
                                                                   "",
                                                                   getFreeDebugPort(),
                                                                   MavenDistributionsCache.resolveEmbeddedMavenHome(),
                                                                   workingDir);
              myIndexingConnector.connect();
            }
            return myIndexingConnector;
          }
        };
    }
    return myIndexerWrapper;
  }

  private MavenIndexerWrapper createLegacyIndexer(@NotNull Project project) {
    String path = project.getBasePath();
    if (path == null) {
      path = new File(".").getPath();
    }
    String finalPath = path;
    if (MavenWslUtil.tryGetWslDistributionForPath(path) != null) {
      return new MavenIndexerWrapper(null) {
        @Override
        protected MavenIndices createMavenIndices() {
          MavenIndices indices = new MavenIndices(this, getIndicesDir().toFile());
          Disposer.register(project, indices);
          return indices;
        }

        @Override
        protected @NotNull MavenServerIndexer create() throws RemoteException {
          return new DummyIndexer();
        }
      };
    }
    return new MavenIndexerWrapper(null) {
      @Override
      protected MavenIndices createMavenIndices() {
        MavenIndices indices = new MavenIndices(this, getIndicesDir().toFile());
        Disposer.register(project, indices);
        return indices;
      }

      @NotNull
      @Override
      protected MavenServerIndexer create() throws RemoteException {
        MavenServerConnector connector;
        synchronized (myMultimoduleDirToConnectorMap) {
          connector = ContainerUtil.find(myMultimoduleDirToConnectorMap.values(), c -> ContainerUtil.find(
            c.myMultimoduleDirectories,
            mDir -> FileUtil
              .isAncestor(finalPath, mDir, false)) != null
          );
        }
        if (connector != null) {
          return connector.createIndexer();
        }
        return MavenServerManager.this.getConnector(project,
                                                    ObjectUtils.chooseNotNull(project.getBasePath(),
                                                                              SystemUtils.getUserHome().getAbsolutePath()
                                                    )
        ).createIndexer();
      }
    };
  }

  public static MavenServerSettings convertSettings(@NotNull Project project, @Nullable MavenGeneralSettings settings) {
    if (settings == null) {
      settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings().getGeneralSettings();
    }
    RemotePathTransformerFactory.Transformer transformer = RemotePathTransformerFactory.createForProject(project);
    MavenServerSettings result = new MavenServerSettings();
    result.setLoggingLevel(settings.getOutputLevel().getLevel());
    result.setOffline(settings.isWorkOffline());
    File mavenHome = settings.getEffectiveMavenHome();
    if (mavenHome != null) {
      String remotePath = transformer.toRemotePath(mavenHome.toPath().toAbsolutePath().toString());
      result.setMavenHomePath(remotePath);
    }


    File userSettings = MavenWslUtil.getUserSettings(project, settings.getUserSettingsFile(), settings.getMavenConfig());
    String userSettingsPath = userSettings.toPath().toAbsolutePath().toString();
    result.setUserSettingsPath(transformer.toRemotePath(userSettingsPath));

    File globalSettings = MavenWslUtil.getGlobalSettings(project, settings.getMavenHome(), settings.getMavenConfig());
    if (globalSettings != null) {
      result.setGlobalSettingsPath(transformer.toRemotePath(globalSettings.toPath().toAbsolutePath().toString()));
    }

    String localRepository = settings.getEffectiveLocalRepository().toPath().toAbsolutePath().toString();

    result.setLocalRepositoryPath(transformer.toRemotePath(localRepository));
    result.setPluginUpdatePolicy(settings.getPluginUpdatePolicy().getServerPolicy());
    result.setSnapshotUpdatePolicy(
      settings.isAlwaysUpdateSnapshots() ? MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE : MavenServerSettings.UpdatePolicy.DO_NOT_UPDATE);
    result.setTychoProject(settings.isTychoProject());
    return result;
  }

  public boolean isUseMaven2() {
    return false;
  }


  /**
   * do not use this method directly, as it is impossible to resolve correct version if maven home is set to wrapper
   * @see MavenDistributionsCache
   */
  @Nullable
  @ApiStatus.Internal
  public static File getMavenHomeFile(@Nullable String mavenHome) {
    if (mavenHome == null) return null;
    for (MavenVersionAwareSupportExtension e : MavenVersionAwareSupportExtension.MAVEN_VERSION_SUPPORT.getExtensionList()) {
      File file = e.getMavenHomeFile(mavenHome);
      if (file != null) return file;
    }

    final File home = new File(mavenHome);
    return MavenUtil.isValidMavenHome(home) ? home : null;
  }
}
