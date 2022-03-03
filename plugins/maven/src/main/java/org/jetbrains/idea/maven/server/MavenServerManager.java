// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
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
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.MavenWslUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.*;
import java.util.function.Predicate;

public final class MavenServerManager implements Disposable {
  public static final String BUNDLED_MAVEN_2 = "Bundled (Maven 2)";
  public static final String BUNDLED_MAVEN_3 = "Bundled (Maven 3)";
  public static final String WRAPPED_MAVEN = "Use Maven wrapper";

  private final Map<String, MavenServerConnector> myMultimoduleDirToConnectorMap = new HashMap<>();
  private File eventListenerJar;


  public Collection<MavenServerConnector> getAllConnectors() {
    Set<MavenServerConnector> set = Collections.newSetFromMap(new IdentityHashMap<>());
    synchronized (myMultimoduleDirToConnectorMap) {
      set.addAll(myMultimoduleDirToConnectorMap.values());
    }
    return set;
  }

  public void cleanUp(MavenServerConnector connector) {
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
          MavenUtil.restartMavenConnectors(project);
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
        connector.shutdown(false);
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
    Integer debugPort = getDebugPort(project);
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
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        synchronized (myMultimoduleDirToConnectorMap) {
          connector.shutdown(false);
        }
      });
    });
  }


  private static Integer getDebugPort(Project project) {
    if ((project.isDefault() && Registry.is("maven.server.debug.default")) ||
        Registry.is("maven.server.debug")) {
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
    try {
      return MavenUtil.getJdk(project, jdkForImporterName);
    }
    catch (ExternalSystemJdkException e) {
      Sdk jdk = MavenUtil.getJdk(project, MavenRunnerSettings.USE_PROJECT_JDK);
      MavenProjectsManager.getInstance(project).getSyncConsole().addWarning(SyncBundle.message("importing.jdk.changed"),
                                                                            SyncBundle.message("importing.jdk.changed.description",
                                                                                               jdkForImporterName, jdk.getName())
      );
      return jdk;
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
  }


  public synchronized void shutdown(boolean wait) {
    Collection<MavenServerConnector> values;
    synchronized (myMultimoduleDirToConnectorMap) {
      values = new ArrayList<>(myMultimoduleDirToConnectorMap.values());
    }

    values.forEach(c -> c.shutdown(wait));
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
        MavenLog.LOG.warn("Event listener does not exist: Please run rebuild for maven modules:\n" +
                          "community/plugins/maven/maven-event-listener\n" +
                          "and all maven*-server* modules"
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
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public String getCurrentMavenVersion() {
    return null;
  }

  /*
  Made public for external systems integration
   */
  //TODO: WSL
  public static List<File> collectClassPathAndLibsFolder(@NotNull MavenDistribution distribution) {
    if (!distribution.isValid()) {
      MavenLog.LOG.warn("Maven Distribution " + distribution + " is not valid");
      throw new IllegalArgumentException("Maven distribution at" + distribution.getMavenHome().toAbsolutePath() + " is not valid");
    }
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final String root = pluginFileOrDir.getParent();

    final List<File> classpath = new ArrayList<>();

    if (pluginFileOrDir.isDirectory()) {
      MavenLog.LOG.debug("collecting classpath for local run");
      prepareClassPathForLocalRunAndUnitTests(distribution.getVersion(), classpath, root);
    }
    else {
      MavenLog.LOG.debug("collecting classpath for production");
      prepareClassPathForProduction(distribution.getVersion(), classpath, root);
    }

    addMavenLibs(classpath, distribution.getMavenHome().toFile());
    MavenLog.LOG.debug("Collected classpath = ", classpath);
    return classpath;
  }

  private static void prepareClassPathForProduction(@NotNull String mavenVersion,
                                                    List<File> classpath,
                                                    String root) {
    classpath.add(new File(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(new File(root, "maven-server-api.jar"));

    if (StringUtil.compareVersionNumbers(mavenVersion, "3") < 0) {
      classpath.add(new File(root, "maven2-server.jar"));
      addDir(classpath, new File(root, "maven2-server-lib"), f -> true);
    }
    else {
      classpath.add(new File(root, "maven3-server-common.jar"));
      addDir(classpath, new File(root, "maven3-server-lib"), f -> true);

      if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
        classpath.add(new File(root, "maven30-server.jar"));
      }
      else {
        classpath.add(new File(root, "maven3-server.jar"));
        if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
          classpath.add(new File(root, "maven36-server.jar"));
        }
      }
    }
  }

  private static void prepareClassPathForLocalRunAndUnitTests(@NotNull String mavenVersion, List<File> classpath, String root) {
    classpath.add(new File(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(new File(root, "intellij.maven.server"));
    File parentFile = MavenUtil.getMavenPluginParentFile();
    if (StringUtil.compareVersionNumbers(mavenVersion, "3") < 0) {
      classpath.add(new File(root, "intellij.maven.server.m2.impl"));
      addDir(classpath, new File(parentFile, "maven2-server-impl/lib"), f -> true);
    }
    else {
      classpath.add(new File(root, "intellij.maven.server.m3.common"));
      addDir(classpath, new File(parentFile, "maven3-server-common/lib"), f -> true);

      if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
        classpath.add(new File(root, "intellij.maven.server.m30.impl"));
      }
      else {
        classpath.add(new File(root, "intellij.maven.server.m3.impl"));
        if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
          classpath.add(new File(root, "intellij.maven.server.m36.impl"));
        }
      }
    }
  }

  private static void addMavenLibs(List<File> classpath, File mavenHome) {
    addDir(classpath, new File(mavenHome, "lib"), f -> !f.getName().contains("maven-slf4j-provider"));
    File bootFolder = new File(mavenHome, "boot");
    File[] classworldsJars = bootFolder.listFiles((dir, name) -> StringUtil.contains(name, "classworlds"));
    if (classworldsJars != null) {
      Collections.addAll(classpath, classworldsJars);
    }
  }

  private static void addDir(List<File> classpath, File dir, Predicate<File> filter) {
    File[] files = dir.listFiles();
    if (files == null) return;

    for (File jar : files) {
      if (jar.isFile() && jar.getName().endsWith(".jar") && filter.test(jar)) {
        classpath.add(jar);
      }
    }
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
          myConnector.shutdown(false);
        }
      }
    };
  }

  public MavenIndexerWrapper createIndexer(@NotNull Project project) {
    String path = project.getBasePath();
    if (path == null) {
      path = new File(".").getPath();
    }
    String finalPath = path;
    if (MavenWslUtil.tryGetWslDistributionForPath(path) != null) {
      return new MavenIndexerWrapper(null, project) {
        @Override
        protected @NotNull MavenServerIndexer create() throws RemoteException {
          return new DummyIndexer();
        }
      };
    }
    return new MavenIndexerWrapper(null, project) {
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

  public void addDownloadListener(MavenServerDownloadListener listener) {
    synchronized (myMultimoduleDirToConnectorMap) {
      myMultimoduleDirToConnectorMap.values().forEach(connector -> connector.addDownloadListener(listener));
    }
  }

  public void removeDownloadListener(MavenServerDownloadListener listener) {
    synchronized (myMultimoduleDirToConnectorMap) {
      myMultimoduleDirToConnectorMap.values().forEach(connector -> connector.removeDownloadListener(listener));
    }
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
      String remotePath = transformer.toRemotePath(mavenHome.getAbsolutePath());
      result.setMavenHomePath(remotePath);
    }


    String userSettingsPath =
      MavenWslUtil.getUserSettings(project, settings.getUserSettingsFile(), settings.getMavenConfig()).getAbsolutePath();
    result.setUserSettingsPath(transformer.toRemotePath(userSettingsPath));

    File globalSettings = MavenWslUtil.getGlobalSettings(project, settings.getMavenHome(), settings.getMavenConfig());
    if (globalSettings != null) {
      result.setGlobalSettingsPath(transformer.toRemotePath(globalSettings.getAbsolutePath()));
    }

    String localRepository = settings.getEffectiveLocalRepository().getAbsolutePath();

    result.setLocalRepositoryPath(transformer.toRemotePath(localRepository));
    result.setPluginUpdatePolicy(settings.getPluginUpdatePolicy().getServerPolicy());
    result.setSnapshotUpdatePolicy(
      settings.isAlwaysUpdateSnapshots() ? MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE : MavenServerSettings.UpdatePolicy.DO_NOT_UPDATE);
    return result;
  }

  public boolean isUseMaven2() {
    return false;
  }


  @Nullable
  @ApiStatus.Internal
  /*
    @do not use this method directly, as it is impossible to resolve correct version if maven home is set to wrapper
   * @see MavenDistributionResolver
   */
  public static File getMavenHomeFile(@Nullable String mavenHome) {
    if (mavenHome == null) return null;
    //will be removed after IDEA-205421
    if (StringUtil.equals(BUNDLED_MAVEN_2, mavenHome) && MavenUtil.isMavenUnitTestModeEnabled()) {
      return resolveEmbeddedMaven2HomeForTests().getMavenHome().toFile();
    }
    if (StringUtil.equals(BUNDLED_MAVEN_3, mavenHome) ||
        StringUtil.equals(MavenProjectBundle.message("maven.bundled.version.title"), mavenHome)) {
      return MavenDistributionsCache.resolveEmbeddedMavenHome().getMavenHome().toFile();
    }
    final File home = new File(mavenHome);
    return MavenUtil.isValidMavenHome(home) ? home : null;
  }


  @NotNull
  private static LocalMavenDistribution resolveEmbeddedMaven2HomeForTests() {
    if (!MavenUtil.isMavenUnitTestModeEnabled()) {
      throw new IllegalStateException("Maven2 is for test purpose only");
    }

    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    if (pluginFileOrDir.isDirectory()) {
      Path parentPath = MavenUtil.getMavenPluginParentFile().toPath();
      return new LocalMavenDistribution(parentPath.resolve("maven2-server-impl/lib/maven2"), BUNDLED_MAVEN_2);
    }
    else {
      throw new IllegalStateException("Maven2 is not bundled anymore, please do not try to use it in tests");
    }
  }
}
