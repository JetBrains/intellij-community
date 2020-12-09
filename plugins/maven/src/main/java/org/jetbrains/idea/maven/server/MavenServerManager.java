// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.groovy.util.SystemUtil;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.MavenDisposable;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.Attributes;

public final class MavenServerManager implements Disposable {
  public static final String BUNDLED_MAVEN_2 = "Bundled (Maven 2)";
  public static final String BUNDLED_MAVEN_3 = "Bundled (Maven 3)";
  public static final String WRAPPED_MAVEN = "Use Maven wrapper";

  private final Map<String, MavenServerConnector> myMultimoduleDirToConnectorMap = new HashMap<>();
  private File eventListenerJar;

  @ApiStatus.Internal
  public void unregisterConnector(MavenServerConnector serverConnector) {
    synchronized (myMultimoduleDirToConnectorMap) {
      myMultimoduleDirToConnectorMap.values().remove(serverConnector);
    }
  }

  public Collection<MavenServerConnector> getAllConnectors() {
    synchronized (myMultimoduleDirToConnectorMap) {
      return new ArrayList<>(myMultimoduleDirToConnectorMap.values());
    }
  }

  public void cleanUp(MavenServerConnector connector) {
    synchronized (myMultimoduleDirToConnectorMap){
      myMultimoduleDirToConnectorMap.entrySet().removeIf(e ->e.getValue() == connector);
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
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
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
  }

  public MavenServerConnector getConnector(@NotNull Project project, @NotNull String workingDirectory) {
    String multimoduleDirectory = getMultimoduleDirectory(project, workingDirectory);
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
    Sdk jdk = getJdk(project, settings);

    synchronized (myMultimoduleDirToConnectorMap) {
      MavenServerConnector connector = myMultimoduleDirToConnectorMap.get(multimoduleDirectory);
      if (connector == null) {
        return registerNewConnectorOrFindCompatible(project, jdk, multimoduleDirectory);
      }
      if (!compatibleParameters(project, connector, jdk, multimoduleDirectory)) {
        connector.shutdown(false);
        return registerNewConnectorOrFindCompatible(project, jdk, multimoduleDirectory);
      }
      return connector;
    }
  }

  private static @NotNull String getMultimoduleDirectory(@NotNull Project project, @NotNull String directory) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject()) {
      return FileUtil.toSystemIndependentName(calculateMultimoduleDirUpToFileTree(directory));
    }
    return FileUtil.toSystemIndependentName(manager.getRootProjects().stream()
      .map(p -> p.getDirectory())
      .filter(rpDirectory -> FileUtil.isAncestor(rpDirectory, directory, false))
      .findFirst()
      .orElse(directory));
  }

  private static String calculateMultimoduleDirUpToFileTree(String directory) {
    VirtualFile path = LocalFileSystem.getInstance().findFileByPath(directory);
    if(path == null) return directory;
    return MavenUtil.getVFileBaseDir(path).getPath();
  }

  private MavenServerConnector registerNewConnectorOrFindCompatible(Project project,
                                                                    Sdk jdk,
                                                                    String multimoduleDirectory) {



    MavenServerConnector existing;
    synchronized (myMultimoduleDirToConnectorMap) {
      existing =
        ContainerUtil.find(myMultimoduleDirToConnectorMap.values(), c -> compatibleParameters(project, c, jdk, multimoduleDirectory));
    }

    if (existing != null) {
      MavenLog.LOG.info("Using existing connector for " + project + " in " + multimoduleDirectory);
      synchronized (myMultimoduleDirToConnectorMap) {
        registerDisposable(project, existing);
        existing.connect(project);
        myMultimoduleDirToConnectorMap.put(multimoduleDirectory, existing);
      }
      return existing;
    }

    MavenDistribution distribution = findMavenDistribution(project, multimoduleDirectory);
    String vmOptions = readVmOptions(project, multimoduleDirectory);
    Integer debugPort = getDebugPort(project);
    MavenServerConnector connector = new MavenServerConnector(this, jdk, vmOptions, debugPort, distribution);
    synchronized (myMultimoduleDirToConnectorMap) {
      myMultimoduleDirToConnectorMap.put(multimoduleDirectory, connector);
      registerDisposable(project, connector);
      connector.connect(project);
    }

    return connector;
  }

  private void registerDisposable(Project project, MavenServerConnector connector) {
    Disposer.register(MavenDisposable.getInstance(project), () -> {
      synchronized (myMultimoduleDirToConnectorMap) {
        connector.shutdown(false);
      }
    });
  }

  private static String readVmOptions(Project project, String multimoduleDirectory) {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(multimoduleDirectory);
    if (baseDir == null) return settings.importingSettings.getVmOptionsForImporter();
    VirtualFile mvn = baseDir.findChild(".mvn");
    if (mvn == null) return settings.importingSettings.getVmOptionsForImporter();
    VirtualFile jdkOpts = mvn.findChild("jvm.config");
    if (jdkOpts == null) return settings.importingSettings.getVmOptionsForImporter();
    try {
      return new String(jdkOpts.contentsToByteArray(true), StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
      return settings.importingSettings.getVmOptionsForImporter();
    }
  }

  private static String getWrapperUrl(Project project,
                               String multimoduleDirectory) {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
    if (useWrapper(settings)) {
      return getWrapperDistributionUrl(multimoduleDirectory);
    }
    return null;
  }

  private static boolean useWrapper(MavenWorkspaceSettings settings) {
    return WRAPPED_MAVEN.equals(settings.generalSettings.getMavenHome()) ||
           StringUtil.equals(settings.generalSettings.getMavenHome(), MavenProjectBundle.message("maven.wrapper.version.title"));
  }

  public static MavenDistribution findMavenDistribution(Project project,
                                                        String multimoduleDirectory) {
    MavenSyncConsole console = MavenProjectsManager.getInstance(project).getSyncConsole();

    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
    if (useWrapper(settings)) {
      String distributionUrl = getWrapperDistributionUrl(multimoduleDirectory);
      if (distributionUrl == null) {
        console.addWarning(SyncBundle.message("cannot.resolve.maven.home"), SyncBundle
          .message("is.not.correct.maven.home.reverting.to.embedded", settings.generalSettings.getMavenHome()));
        return resolveEmbeddedMavenHome();
      }
      else {
        return doResolveMavenWrapper(console, distributionUrl, multimoduleDirectory);
      }
    }
    else {
      return new MavenDistributionConverter().fromString(settings.generalSettings.getMavenHome());
    }
  }

  @NotNull
  private static MavenDistribution doResolveMavenWrapper(MavenSyncConsole console, String distributionUrl, String multimoduleDirectory) {
    try {
      console.startWrapperResolving();
      MavenDistribution distribution =
        new MavenWrapperSupport().downloadAndInstallMaven(distributionUrl, console.progressIndicatorForWrapper());
      if (distributionUrl.toLowerCase(Locale.ENGLISH).startsWith("http:")) {
        MavenWrapperSupport.showUnsecureWarning(console, LocalFileSystem.getInstance().findFileByPath(multimoduleDirectory));
      }
      console.finishWrapperResolving(null);
      return distribution;
    }
    catch (Exception e) {
      console.finishWrapperResolving(e);
      LocalMavenDistribution distribution = resolveEmbeddedMavenHome();
      return new LocalMavenDistribution(distribution.getMavenHome(), distributionUrl);
    }
  }

  private static @Nullable String getWrapperDistributionUrl(String multimoduleDirectory) {
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(multimoduleDirectory);
    if (baseDir == null) {
      return null;
    }
    return MavenWrapperSupport.getWrapperDistributionUrl(baseDir);
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
    String jdkForImporterName = settings.importingSettings.getJdkForImporter();
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

    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
    MavenDistribution distribution = null;
    if (useWrapper(settings)) {//todo: this weird logic to avoid trying to resolve unresolved wrapper several times
      String distributionUrl = getWrapperDistributionUrl(multimoduleDirectory);
      if(connector.getMavenDistribution().getName().equals(distributionUrl)) {
        distribution = connector.getMavenDistribution();
      }
    }
    if(distribution == null){
      distribution = findMavenDistribution(project, multimoduleDirectory);
    }
    String vmOptions = readVmOptions(project, multimoduleDirectory);
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
    values.forEach(c -> c.shutdownForce(wait));
  }

  public static boolean verifyMavenSdkRequirements(@NotNull Sdk jdk, String mavenVersion) {
    String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.3.1") >= 0
        && StringUtil.compareVersionNumbers(version, "1.7") < 0) {
      return false;
    }
    return true;
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

  @Nullable
  public String getMavenVersion(@Nullable String mavenHome) {
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
  public String getCurrentMavenVersion() {
    return null;
  }

  /*
  Made public for external systems integration
   */
  public static List<File> collectClassPathAndLibsFolder(@NotNull String mavenVersion, @NotNull File mavenHome) {
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final String root = pluginFileOrDir.getParent();

    final List<File> classpath = new ArrayList<>();

    if (pluginFileOrDir.isDirectory()) {
      prepareClassPathForLocalRunAndUnitTests(mavenVersion, classpath, root);
    }
    else {
      prepareClassPathForProduction(mavenVersion, classpath, root);
    }

    addMavenLibs(classpath, mavenHome);
    MavenLog.LOG.debug("Collected classpath = ", classpath);
    return classpath;
  }

  private static void prepareClassPathForProduction(@NotNull String mavenVersion,
                                                    List<File> classpath,
                                                    String root) {
    classpath.add(new File(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(new File(root, "maven-server-api.jar"));

    if (StringUtil.compareVersionNumbers(mavenVersion, "3") < 0) {
      classpath.add(new File(root, "maven2-server-impl.jar"));
      addDir(classpath, new File(root, "maven2-server-lib"), f -> true);
    }
    else {
      classpath.add(new File(root, "maven3-server-common.jar"));
      addDir(classpath, new File(root, "maven3-server-lib"), f -> true);

      if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
        classpath.add(new File(root, "maven30-server-impl.jar"));
      }
      else {
        classpath.add(new File(root, "maven3-server-impl.jar"));
        if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
          classpath.add(new File(root, "maven36-server-impl.jar"));
        }
      }
    }
  }

  private static void prepareClassPathForLocalRunAndUnitTests(@NotNull String mavenVersion, List<File> classpath, String root) {
    classpath.add(new File(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(new File(root, "intellij.maven.server"));
    File parentFile = getMavenPluginParentFile();
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

  private static File getMavenPluginParentFile() {
    File luceneLib = new File(PathUtil.getJarPathForClass(Query.class));
    return luceneLib.getParentFile().getParentFile().getParentFile();
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

  public MavenEmbedderWrapper createEmbedder(final Project project,
                                             final boolean alwaysOnline,
                                             @Nullable String workingDirectory,
                                             @Nullable String multiModuleProjectDirectory) {

    return new MavenEmbedderWrapper(null) {
      @NotNull
      @Override
      protected MavenServerEmbedder create() throws RemoteException {
        MavenServerSettings settings = convertSettings(MavenProjectsManager.getInstance(project).getGeneralSettings());
        if (alwaysOnline && settings.isOffline()) {
          settings = settings.clone();
          settings.setOffline(false);
        }

        settings.setProjectJdk(MavenUtil.getSdkPath(ProjectRootManager.getInstance(project).getProjectSdk()));
        return MavenServerManager.this.getConnector(project, multiModuleProjectDirectory)
          .createEmbedder(new MavenEmbedderSettings(settings, workingDirectory, multiModuleProjectDirectory));
      }
    };
  }

  public MavenIndexerWrapper createIndexer(@NotNull Project project) {
    return new MavenIndexerWrapper(null) {
      @NotNull
      @Override
      protected MavenServerIndexer create() throws RemoteException {
        synchronized (myMultimoduleDirToConnectorMap){
          MavenServerConnector connector = myMultimoduleDirToConnectorMap.values().stream().findFirst().orElse(null);
          if(connector!=null){
            connector.connect(project);
            return connector.createIndexer();
          }
        }
        return MavenServerManager.this.getConnector(project,
                                                    ObjectUtils.chooseNotNull(project.getBasePath(),
                                                                              SystemUtils.getUserHome().getAbsolutePath()
                                                    )
        ).createIndexer();
      }
    };
  }

  /**
   * @deprecated use {@link MavenServerManager#createIndexer(Project)}
   */
  @Deprecated
  public MavenIndexerWrapper createIndexer() {
    return createIndexer(ProjectManager.getInstance().getDefaultProject());
  }

  public void addDownloadListener(MavenServerDownloadListener listener) {
    synchronized (myMultimoduleDirToConnectorMap) {
      myMultimoduleDirToConnectorMap.values().forEach(l -> l.addDownloadListener(listener));
    }
  }

  public void removeDownloadListener(MavenServerDownloadListener listener) {
    synchronized (myMultimoduleDirToConnectorMap) {
      myMultimoduleDirToConnectorMap.values().forEach(l -> l.removeDownloadListener(listener));
    }
  }

  public static MavenServerSettings convertSettings(MavenGeneralSettings settings) {
    MavenServerSettings result = new MavenServerSettings();
    result.setLoggingLevel(settings.getOutputLevel().getLevel());
    result.setOffline(settings.isWorkOffline());
    result.setMavenHome(settings.getEffectiveMavenHome());
    result.setUserSettingsFile(settings.getEffectiveUserSettingsIoFile());
    result.setGlobalSettingsFile(settings.getEffectiveGlobalSettingsIoFile());
    result.setLocalRepository(settings.getEffectiveLocalRepository());
    result.setPluginUpdatePolicy(settings.getPluginUpdatePolicy().getServerPolicy());
    result.setSnapshotUpdatePolicy(
      settings.isAlwaysUpdateSnapshots() ? MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE : MavenServerSettings.UpdatePolicy.DO_NOT_UPDATE);
    return result;
  }

  public boolean isUseMaven2() {
    final String version = getCurrentMavenVersion();
    return version != null && StringUtil.compareVersionNumbers(version, "3") < 0 && StringUtil.compareVersionNumbers(version, "2") >= 0;
  }


  @Nullable
  @Deprecated
  /*
    @deprecated do not use this method, as it is impossible to resolve correct version if maven home is set to wrapper
   * @see findMavenDistribution
   */
  public static File getMavenHomeFile(@Nullable String mavenHome) {
    if (mavenHome == null) return null;
    //will be removed after IDEA-205421
    if (StringUtil.equals(BUNDLED_MAVEN_2, mavenHome) && ApplicationManager.getApplication().isUnitTestMode()) {
      return resolveEmbeddedMaven2HomeForTests().getMavenHome();
    }
    if (StringUtil.equals(BUNDLED_MAVEN_3, mavenHome) ||
        StringUtil.equals(MavenProjectBundle.message("maven.bundled.version.title"), mavenHome)) {
      return resolveEmbeddedMavenHome().getMavenHome();
    }
    final File home = new File(mavenHome);
    return MavenUtil.isValidMavenHome(home) ? home : null;
  }

  /**
   * @deprecated use MavenImportingSettings.setVmOptionsForImporter
   */
  @NotNull
  @Deprecated
  public String getMavenEmbedderVMOptions() {
    return "";
  }


  /**
   * @deprecated use MavenImportingSettings.setVmOptionsForImporter
   */
  @Deprecated
  public void setMavenEmbedderVMOptions(@NotNull String mavenEmbedderVMOptions) {
  }

  @NotNull
  public static LocalMavenDistribution resolveEmbeddedMavenHome() {
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final String root = pluginFileOrDir.getParent();
    if (pluginFileOrDir.isDirectory()) {
      File parentFile = getMavenPluginParentFile();
      return new LocalMavenDistribution(new File(parentFile, "maven36-server-impl/lib/maven3"), BUNDLED_MAVEN_3);
    }
    else {
      return new LocalMavenDistribution(new File(root, "maven3"), BUNDLED_MAVEN_3);
    }
  }

  @NotNull
  private static LocalMavenDistribution resolveEmbeddedMaven2HomeForTests() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("Maven2 is for test purpose only");
    }

    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    if (pluginFileOrDir.isDirectory()) {
      File parentFile = getMavenPluginParentFile();
      return new LocalMavenDistribution(new File(parentFile, "maven2-server-impl/lib/maven2"), BUNDLED_MAVEN_2);
    }
    else {
      throw new IllegalStateException("Maven2 is not bundled anymore, please do not try to use it in tests");
    }
  }
}
