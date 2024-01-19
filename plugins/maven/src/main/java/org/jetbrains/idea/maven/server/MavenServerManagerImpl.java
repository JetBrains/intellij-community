// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.trustedProjects.TrustedProjectsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.MavenDisposable;
import org.jetbrains.idea.maven.config.MavenConfig;
import org.jetbrains.idea.maven.config.MavenConfigSettings;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.indices.MavenIndices;
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.MavenWslUtil;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.jetbrains.idea.maven.server.DummyMavenServerConnector.isDummy;

final class MavenServerManagerImpl implements MavenServerManager {
  private final Map<String, MavenServerConnector> myMultimoduleDirToConnectorMap = new HashMap<>();
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);
  //TODO: should be replaced by map, where key is the indexing directory. (local/wsl)
  private MavenIndexingConnectorImpl myIndexingConnector = null;
  private MavenIndexerWrapper myIndexerWrapper = null;

  private File eventListenerJar;

  MavenServerManagerImpl() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appWillBeClosed(boolean isRestart) {
        isShutdown.set(true);
        closeAllConnectorsEventually();
      }
    });

    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        if (MavenUtil.INTELLIJ_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
          isShutdown.set(true);
          closeAllConnectorsEventually();
        }
      }
    });

    connection.subscribe(TrustedProjectsListener.TOPIC, new TrustedProjectsListener() {
      @Override
      public void onProjectTrusted(@NotNull Project project) {
        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        if (manager.isMavenizedProject()) {
          MavenUtil.restartMavenConnectors(project, true, it -> isDummy(it));
        }
      }

      @Override
      public void onProjectUntrusted(@NotNull Project project) {
        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        if (manager.isMavenizedProject()) {
          MavenUtil.restartMavenConnectors(project, true, it -> !isDummy(it));
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

  @Override
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

  @Override
  public void restartMavenConnectors(Project project, boolean wait, Predicate<MavenServerConnector> condition) {
    List<MavenServerConnector> connectorsToShutDown = new ArrayList<>();
    synchronized (myMultimoduleDirToConnectorMap) {
      getAllConnectors().forEach(it -> {
        if (project.equals(it.getProject()) && condition.test(it)) {
          connectorsToShutDown.add(removeConnector(it));
        }
      });
    }
    MavenProjectsManager.getInstance(project).getEmbeddersManager().reset();
    MavenServerManagerEx.stopConnectors(project, wait, connectorsToShutDown);
  }

  private MavenServerConnector doGetConnector(@NotNull Project project, @NotNull String workingDirectory) {

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
    if (MavenLog.LOG.isTraceEnabled()) {
      MavenLog.LOG.trace("[connector] get " + connector);
    }
    return connector;
  }

  @Override
  public MavenServerConnector getConnector(@NotNull Project project, @NotNull String workingDirectory) {
    var connector = doGetConnector(project, workingDirectory);
    if (!connector.ping()) {
      shutdownConnector(connector, true);
      connector = doGetConnector(project, workingDirectory);
    }
    return connector;
  }

  private MavenServerConnector doGetOrCreateConnector(@NotNull Project project,
                                                      @NotNull String multimoduleDirectory,
                                                      @NotNull Sdk jdk) {
    if (isShutdown.get()) {
      throw new IllegalStateException("We are closed, sorry. No connectors anymore");
    }
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
      var connectorFactory = ApplicationManager.getApplication().getService(MavenServerConnectorFactory.class);
      connector = connectorFactory.create(project, jdk, vmOptions, debugPort, distribution, multimoduleDirectory);
      MavenLog.LOG.debug("[connector] new maven connector " + connector);
    }
    else {
      MavenLog.LOG.warn("Project " + project + " not trusted enough. Will not start maven for it");
      connector = new DummyMavenServerConnector(project, jdk, vmOptions, distribution, multimoduleDirectory);
    }
    registerDisposable(project, connector);
    return connector;
  }

  private void registerDisposable(Project project, MavenServerConnector connector) {
    Disposer.register(MavenDisposable.getInstance(project), () -> {
      ApplicationManager.getApplication().executeOnPooledThread(() -> shutdownConnector(connector, false));
    });
  }

  @Override
  public void dispose() {
    closeAllConnectorsAndWait();
  }

  @Override
  public boolean shutdownConnector(MavenServerConnector connector, boolean wait) {
    MavenServerConnector connectorToStop = removeConnector(connector);
    if (connectorToStop == null) return false;
    connectorToStop.stop(wait);
    return true;
  }

  private MavenServerConnector removeConnector(@Nullable MavenServerConnector connector) {
    if (connector == null) return null;
    synchronized (myMultimoduleDirToConnectorMap) {
      if (myIndexingConnector == connector) {
        myIndexingConnector = null;
        myIndexerWrapper = null;
        return connector;
      }
      if (!myMultimoduleDirToConnectorMap.containsValue(connector)) {
        return null;
      }
      myMultimoduleDirToConnectorMap.entrySet().removeIf(e -> e.getValue() == connector);
    }
    return connector;
  }

  /**
   * use MavenUtil.restartMavenConnectors
   */
  @Override
  @TestOnly
  public void closeAllConnectorsAndWait() {
    shutdownNow();
  }

  private void closeAllConnectorsEventually() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      shutdownNow();
    });
  }

  private void shutdownNow() {
    Collection<MavenServerConnector> values;
    synchronized (myMultimoduleDirToConnectorMap) {
      values = new ArrayList<>(myMultimoduleDirToConnectorMap.values());
    }

    shutdownConnector(myIndexingConnector, true);
    values.forEach(c -> shutdownConnector(c, true));
  }



  @Override
  public File getMavenEventListener() {
    return getEventListenerJar();
  }

  private File getEventListenerJar() {
    if (eventListenerJar != null) {
      return eventListenerJar;
    }
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final String root = pluginFileOrDir.getParent();
    if (pluginFileOrDir.isDirectory()) {
      eventListenerJar = getEventSpyPathForLocalBuild();
      if (!eventListenerJar.exists()) {
        MavenLog.LOG.warn("""
                            Event listener does not exist: Please run rebuild for maven modules:
                            community/plugins/maven/maven-event-listener"""
        );
      }
    }
    else {
      eventListenerJar = new File(root, "maven-event-listener.jar");
      if (!eventListenerJar.exists()) {
        MavenLog.LOG.warn("Event listener does not exist at " + eventListenerJar +
                          ". It should be built as part of plugin layout process and bundled along with maven plugin jars");
      }
    }
    return eventListenerJar;
  }

  @Override
  @NotNull
  public MavenEmbedderWrapper createEmbedder(final Project project,
                                             final boolean alwaysOnline,
                                             @NotNull String multiModuleProjectDirectory) {

    return new MavenEmbedderWrapper(project) {
      private MavenServerConnector myConnector;

      @NotNull
      @Override
      protected synchronized MavenServerEmbedder create() throws RemoteException {
        MavenServerSettings settings =
          convertSettings(project, MavenProjectsManager.getInstance(project).getGeneralSettings(), multiModuleProjectDirectory);
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

        var forceResolveDependenciesSequentially = Registry.is("maven.server.force.resolve.dependencies.sequentially");
        var useCustomDependenciesResolver = Registry.is("maven.server.use.custom.dependencies.resolver");

        myConnector = MavenServerManagerImpl.this.getConnector(project, multiModuleProjectDirectory);
        return myConnector.createEmbedder(new MavenEmbedderSettings(
          settings,
          transformer.toRemotePath(multiModuleProjectDirectory),
          forceResolveDependenciesSequentially,
          useCustomDependenciesResolver
        ));
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

  @Override
  public MavenIndexerWrapper createIndexer() {
    return createDedicatedIndexer();
  }

  @Override
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
          protected MavenIndices createMavenIndices(Project project) {
            MavenIndices indices = new MavenIndices(this, MavenSystemIndicesManager.getInstance().getIndicesDir().toFile(), project);
            Disposer.register(MavenDisposable.getInstance(project), indices);
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

          private MavenServerConnector getIndexingConnector() {
            if (myIndexingConnector != null) return myIndexingConnector;
            Sdk jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
            synchronized (myMultimoduleDirToConnectorMap) {
              if (myIndexingConnector != null) return myIndexingConnector;
              myIndexingConnector = new MavenIndexingConnectorImpl(jdk,
                                                                   "",
                                                                   getFreeDebugPort(),
                                                                   MavenDistributionsCache.resolveEmbeddedMavenHome(),
                                                                   workingDir);
            }
            myIndexingConnector.connect();
            return myIndexingConnector;
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
        protected MavenIndices createMavenIndices(Project project) {
          MavenIndices indices = new MavenIndices(this, MavenSystemIndicesManager.getInstance().getIndicesDir().toFile(), project);
          Disposer.register(MavenDisposable.getInstance(project), indices);
          return indices;
        }

        @Override
        protected @NotNull MavenServerIndexer create() {
          return new DummyIndexer();
        }
      };
    }
    return new MavenIndexerWrapper(null) {
      @Override
      protected MavenIndices createMavenIndices(Project project) {
        MavenIndices indices = new MavenIndices(this, MavenSystemIndicesManager.getInstance().getIndicesDir().toFile(), project);
        Disposer.register(MavenDisposable.getInstance(project), indices);
        return indices;
      }

      @NotNull
      @Override
      protected MavenServerIndexer create() throws RemoteException {
        MavenServerConnector connector;
        synchronized (myMultimoduleDirToConnectorMap) {
          connector = ContainerUtil.find(myMultimoduleDirToConnectorMap.values(), c -> ContainerUtil.find(
            c.getMultimoduleDirectories(),
            mDir -> FileUtil
              .isAncestor(finalPath, mDir, false)) != null
          );
        }
        if (connector != null) {
          return connector.createIndexer();
        }
        String workingDirectory = ObjectUtils.chooseNotNull(project.getBasePath(), SystemUtils.getUserHome().getAbsolutePath());
        return MavenServerManagerImpl.this.getConnector(project, workingDirectory).createIndexer();
      }
    };
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

  private static File getEventSpyPathForLocalBuild() {
    final File root = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    return new File(root.getParent(), "intellij.maven.server.eventListener");
  }

  private static MavenServerSettings convertSettings(@NotNull Project project,
                                                     @Nullable MavenGeneralSettings settings,
                                                     @NotNull String multiModuleProjectDirectory) {
    if (settings == null) {
      settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings().getGeneralSettings();
    }
    RemotePathTransformerFactory.Transformer transformer = RemotePathTransformerFactory.createForProject(project);
    MavenServerSettings result = new MavenServerSettings();
    result.setLoggingLevel(settings.getOutputLevel().getLevel());
    result.setOffline(settings.isWorkOffline());
    result.setUpdateSnapshots(settings.isAlwaysUpdateSnapshots());
    MavenDistribution mavenDistribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multiModuleProjectDirectory);

    String remotePath = transformer.toRemotePath(mavenDistribution.getMavenHome().toString());
    result.setMavenHomePath(remotePath);

    File userSettings = MavenWslUtil.getUserSettings(project, settings.getUserSettingsFile(), settings.getMavenConfig());
    String userSettingsPath = userSettings.toPath().toAbsolutePath().toString();
    result.setUserSettingsPath(transformer.toRemotePath(userSettingsPath));

    String localRepository =
      MavenWslUtil.getLocalRepo(project, settings.getLocalRepository(), new MavenInSpecificPath(mavenDistribution.getMavenHome().toFile()),
                                settings.getUserSettingsFile(),
                                settings.getMavenConfig()).getAbsolutePath();
    result.setLocalRepositoryPath(transformer.toRemotePath(localRepository));
    File file = getGlobalConfigFromMavenConfig(project, settings, transformer);
    if (file == null) {
      file = MavenUtil.resolveGlobalSettingsFile(mavenDistribution.getMavenHome().toFile());
    }
    result.setGlobalSettingsPath(transformer.toRemotePath(file.getAbsolutePath()));
    return result;
  }

  private static @Nullable File getGlobalConfigFromMavenConfig(@NotNull Project project,
                                                               @NotNull MavenGeneralSettings settings,
                                                               RemotePathTransformerFactory.Transformer transformer) {

    MavenConfig mavenConfig = settings.getMavenConfig();
    if (mavenConfig == null) return null;
    String filePath = mavenConfig.getFilePath(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS);
    if (filePath == null) return null;
    return new File(filePath);
  }
}
