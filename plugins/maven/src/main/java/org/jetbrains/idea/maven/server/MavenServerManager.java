// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.build.events.MessageEvent;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Attribute;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.jar.Attributes;

@State(
  name = "MavenVersion",
  storages = @Storage(value = "mavenVersion.xml", deprecated = true)
)
public class MavenServerManager implements PersistentStateComponent<MavenServerManager.State>,
                                           Disposable {

  public static final String BUNDLED_MAVEN_2 = "Bundled (Maven 2)";
  public static final String BUNDLED_MAVEN_3 = "Bundled (Maven 3)";
  public static final String WRAPPER_MAVEN = "Defined by wrapper";

  private static final String DEFAULT_VM_OPTIONS =
    "-Xmx768m";

  private final Map<Project, MavenServerConnector> myServerConnectors = new ConcurrentHashMap<>();
  private File eventListenerJar;

  public boolean checkMavenSettings(Project project, MavenSyncConsole console) {

    MavenDistribution distribution = MavenDistribution.fromSettings(project);
    if (distribution == null) {
      console.showQuickFixBadMaven(SyncBundle.message("maven.sync.quickfixes.nomaven"), MessageEvent.Kind.ERROR);
      return false;
    }

    if (StringUtil.compareVersionNumbers(distribution.getVersion(), "3.6.0") == 0) {
      console.showQuickFixBadMaven(SyncBundle.message("maven.sync.quickfixes.maven360"), MessageEvent.Kind.WARNING);
      return false;
    }

    Sdk jdk = getJdk(project);
    if (!verifyMavenSdkRequirements(jdk, distribution.getVersion())) {
      console.showQuickFixJDK(distribution.getVersion());
      return false;
    }
    return true;
  }

  public void unregisterConnector(MavenServerConnector serverConnector) {
    myServerConnectors.values().remove(serverConnector);
  }

  public void shutdownServer(Project project) {
    MavenServerConnector connector = myServerConnectors.get(project);
    if (connector != null) {
      connector.shutdown(true);
    }
  }

  public Collection<MavenServerConnector> getAllConnectors() {
    return Collections.unmodifiableCollection(myServerConnectors.values());
  }

  static class State {
    @Deprecated
    @Attribute(value = "version", converter = UseMavenConverter.class)
    public boolean useMaven2;
    @Attribute
    public String vmOptions = DEFAULT_VM_OPTIONS;
    @Attribute
    public String embedderJdk = MavenRunnerSettings.USE_INTERNAL_JAVA;
    @Attribute(converter = MavenDistributionConverter.class)
    @Nullable
    public MavenDistribution mavenHome;
    @Attribute
    public MavenExecutionOptions.LoggingLevel loggingLevel = MavenExecutionOptions.LoggingLevel.INFO;
  }

  public static MavenServerManager getInstance() {
    return ServiceManager.getService(MavenServerManager.class);
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

  public MavenServerConnector getConnector(MavenProject mavenProject, @NotNull Project project) {
    return getConnector(project);
  }

  public MavenServerConnector getConnector(@NotNull Project project) {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
    MavenDistribution distribution = new MavenDistributionConverter().fromString(settings.generalSettings.getMavenHome());
    if (distribution == null) {
      throw new RuntimeException("Maven not found Version"); //TODO
    }
    Sdk jdk = getJdk(project);

    if (!verifyMavenSdkRequirements(jdk, distribution.getVersion())) {
      throw new RuntimeException("Wrong JDK Version"); //TODO
    }
    MavenServerConnector connector = myServerConnectors.get(project);
    if (connector == null) {
      connector = myServerConnectors.computeIfAbsent(project, p -> new MavenServerConnector(p, this, settings,  jdk));
      registerDisposable(project, connector);

      return connector;
    }

    if (!compatibleParameters(connector, jdk, settings.importingSettings.getVmOptionsForImporter())) {
      connector.shutdown(false);
      connector = new MavenServerConnector(project, this, settings, jdk);
      registerDisposable(project, connector);
      myServerConnectors.put(project, connector);
    }
    return connector;
  }

  @NotNull
  private Sdk getJdk(Project project) {
    if(ApplicationManager.getApplication().isUnitTestMode()) {
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }
    Sdk jdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (jdk == null) {
      MavenLog.LOG.warn("cannot find JDK for project " + project);
      jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }
    return jdk;
  }

  private void registerDisposable(Project project, MavenServerConnector connector) {
    if (project.isDefault()) {
      Disposer.register(this, connector);
    }
    else {
      Disposer.register(project, connector);
    }
  }

  private boolean compatibleParameters(MavenServerConnector connector, Sdk jdk, String vmOptions) {
    return StringUtil.equals(connector.getJdk().getName(), jdk.getName()) && StringUtil.equals(vmOptions, connector.getVMOptions());
  }

  public MavenServerConnector getDefaultConnector() {
    return getConnector(ProjectManager.getInstance().getDefaultProject());
  }

  @Override
  public void dispose() {
  }


  public synchronized void shutdown(boolean wait) {
    myServerConnectors.values().forEach(c -> c.shutdown(wait));
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

  @SuppressWarnings("unused")
  @Nullable
  public String getMavenVersion(@Nullable File mavenHome) {
    return MavenUtil.getMavenVersion(mavenHome);
  }

  @Nullable
  @Deprecated
  /**
   * @deprecated
   * use {@link MavenGeneralSettings.mavenHome} and {@link MavenUtil.getMavenVersion}
   */
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
        return MavenServerManager.this.getConnector(project)
          .createEmbedder(new MavenEmbedderSettings(settings, workingDirectory, multiModuleProjectDirectory));
      }
    };
  }

  public MavenIndexerWrapper createIndexer() {
    return new MavenIndexerWrapper(null) {
      @NotNull
      @Override
      protected MavenServerIndexer create() throws RemoteException {
        return MavenServerManager.this.getDefaultConnector().createIndexer();
      }
    };
  }

  public void addDownloadListener(MavenServerDownloadListener listener) {
    myServerConnectors.values().forEach(l -> l.addDownloadListener(listener));
  }

  public void removeDownloadListener(MavenServerDownloadListener listener) {
    myServerConnectors.values().forEach(l -> l.removeDownloadListener(listener));
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

  private static class UseMavenConverter extends Converter<Boolean> {
    @Nullable
    @Override
    public Boolean fromString(@NotNull String value) {
      return "2.x".equals(value);
    }

    @NotNull
    @Override
    public String toString(@NotNull Boolean value) {
      return value ? "2.x" : "3.x";
    }
  }

  public boolean isUseMaven2() {
    final String version = getCurrentMavenVersion();
    return version != null && StringUtil.compareVersionNumbers(version, "3") < 0 && StringUtil.compareVersionNumbers(version, "2") >= 0;
  }


  @Nullable
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

  @NotNull
  @Deprecated
  /**
   * @deprecated use MavenImportingSettings.setVmOptionsForImporter
   */
  public String getMavenEmbedderVMOptions() {
    return "";
  }


  @Deprecated
  /**
   * @deprecated use MavenImportingSettings.setVmOptionsForImporter
   */
  public void setMavenEmbedderVMOptions(@NotNull String mavenEmbedderVMOptions) {
  }


  @Nullable
  @Override
  public State getState() {
    return null;
  }

  @Override
  public void loadState(@NotNull State state) {
  }

  @NotNull
  public static MavenDistribution resolveEmbeddedMavenHome() {
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final String root = pluginFileOrDir.getParent();
    if (pluginFileOrDir.isDirectory()) {
      File parentFile = getMavenPluginParentFile();
      return new MavenDistribution(new File(parentFile, "maven36-server-impl/lib/maven3"), BUNDLED_MAVEN_3);
    }
    else {
      return new MavenDistribution(new File(root, "maven3"), BUNDLED_MAVEN_3);
    }
  }

  @NotNull
  private static MavenDistribution resolveEmbeddedMaven2HomeForTests() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("Maven2 is for test purpose only");
    }

    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    if (pluginFileOrDir.isDirectory()) {
      File parentFile = getMavenPluginParentFile();
      return new MavenDistribution(new File(parentFile, "maven2-server-impl/lib/maven2"), BUNDLED_MAVEN_2);
    }
    else {
      throw new IllegalStateException("Maven2 is not bundled anymore, please do not try to use it in tests");
    }
  }
}
