// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Attribute;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.apache.lucene.search.Query;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.slf4j.Logger;
import org.slf4j.impl.Log4jLoggerFactory;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.Attributes;

@State(
  name = "MavenVersion",
  storages = @Storage("mavenVersion.xml")
)
public class MavenServerManager extends RemoteObjectWrapper<MavenServer> implements PersistentStateComponent<MavenServerManager.State>,
                                                                                    Disposable {

  public static final String BUNDLED_MAVEN_2 = "Bundled (Maven 2)";
  public static final String BUNDLED_MAVEN_3 = "Bundled (Maven 3)";

  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.server.RemoteMavenServer";
  @NonNls private static final String MAIN_CLASS36 = "org.jetbrains.idea.maven.server.RemoteMavenServer36";

  private static final String DEFAULT_VM_OPTIONS = "-Xmx768m";

  private static final String FORCE_MAVEN2_OPTION = "-Didea.force.maven2";

  private final RemoteProcessSupport<Object, MavenServer, Object> mySupport;

  private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
  private final RemoteMavenServerDownloadListener myDownloadListener = new RemoteMavenServerDownloadListener();
  private boolean myLoggerExported;
  private boolean myDownloadListenerExported;
  private State myState = new State();

  private static class BundledMavenPathHolder {
    private static final File myBundledMaven2Home;
    private static final File myBundledMaven3Home;
    private static final File eventListenerJar;

    static {
      final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
      final String root = pluginFileOrDir.getParent();
      if (pluginFileOrDir.isDirectory()) {
        File parentFile = getMavenPluginParentFile();
        myBundledMaven2Home = new File(parentFile, "maven2-server-impl/lib/maven2");
        myBundledMaven3Home = new File(parentFile, "maven36-server-impl/lib/maven3");
        eventListenerJar = getEventSpyPathForLocalBuild();
      }
      else {
        myBundledMaven2Home = new File(root, "maven2");
        myBundledMaven3Home = new File(root, "maven3");
        eventListenerJar = new File(root, "maven-event-listener.jar");
      }

      if (!myBundledMaven3Home.exists()) {
        if (ApplicationManager.getApplication().isInternal()) {
          MavenLog.LOG.error("Cannot find bundled maven " + myBundledMaven3Home + " please run setupBundledMaven.gradle script");
        }
        else {
          MavenLog.LOG.error("Cannot find bundled maven " + myBundledMaven3Home);
        }
      }

      if (!eventListenerJar.exists()) {
        MavenLog.LOG.error("Event listener does not exist " + eventListenerJar);
      }
    }

    private static File getEventSpyPathForLocalBuild() {
      final File root = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
      return new File(root.getParent(), "intellij.maven.server.eventListener");
    }
  }

  static class State {
    @Deprecated
    @Attribute(value = "version", converter = UseMavenConverter.class)
    public boolean useMaven2;
    @Attribute
    public String vmOptions = DEFAULT_VM_OPTIONS;
    @Attribute
    public String embedderJdk = MavenRunnerSettings.USE_INTERNAL_JAVA;
    @Attribute
    public String mavenHome = BUNDLED_MAVEN_3;
    @Attribute
    public MavenExecutionOptions.LoggingLevel loggingLevel = MavenExecutionOptions.LoggingLevel.INFO;
  }

  public static MavenServerManager getInstance() {
    return ServiceManager.getService(MavenServerManager.class);
  }

  public MavenServerManager() {
    super(null);

    mySupport = new RemoteProcessSupport<Object, MavenServer, Object>(MavenServer.class) {
      @Override
      protected void fireModificationCountChanged() {
      }

      @Override
      protected String getName(@NotNull Object file) {
        return MavenServerManager.class.getSimpleName();
      }

      @Override
      protected RunProfileState getRunProfileState(@NotNull Object target, @NotNull Object configuration, @NotNull Executor executor) {
        return new MavenServerCMDState();
      }
    };
  }

  @Override
  public void dispose() {
    shutdown(false);
  }

  @Override
  @NotNull
  protected synchronized MavenServer create() throws RemoteException {
    MavenServer result;
    try {
      result = mySupport.acquire(this, "");
    }
    catch (Exception e) {
      throw new RemoteException("Cannot start maven service", e);
    }

    myLoggerExported = UnicastRemoteObject.exportObject(myLogger, 0) != null;
    if (!myLoggerExported) throw new RemoteException("Cannot export logger object");

    myDownloadListenerExported = UnicastRemoteObject.exportObject(myDownloadListener, 0) != null;
    if (!myDownloadListenerExported) throw new RemoteException("Cannot export download listener object");

    result.set(myLogger, myDownloadListener);

    return result;
  }

  public synchronized void shutdown(boolean wait) {
    mySupport.stopAll(wait);
    cleanup();
  }

  @Override
  protected synchronized void cleanup() {
    super.cleanup();

    if (myLoggerExported) {
      try {
        UnicastRemoteObject.unexportObject(myLogger, true);
      }
      catch (RemoteException e) {
        MavenLog.LOG.error(e);
      }
      myLoggerExported = false;
    }
    if (myDownloadListenerExported) {
      try {
        UnicastRemoteObject.unexportObject(myDownloadListener, true);
      }
      catch (RemoteException e) {
        MavenLog.LOG.error(e);
      }
      myDownloadListenerExported = false;
    }
  }

  @NotNull
  private Sdk getJdk() {
    if (myState.embedderJdk.equals(MavenRunnerSettings.USE_JAVA_HOME)) {
      final String javaHome = EnvironmentUtil.getEnvironmentMap().get("JAVA_HOME");
      if (!StringUtil.isEmptyOrSpaces(javaHome)) {
        return JavaSdk.getInstance().createJdk("", javaHome);
      }
    }

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(myState.embedderJdk)) {
        return projectJdk;
      }
    }

    // By default use internal jdk
    return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
  }

  public static void verifyMavenSdkRequirements(@NotNull Sdk jdk, String mavenVersion, @NotNull String sdkConfigLocation) {
    String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.3.1") >= 0
        && StringUtil.compareVersionNumbers(version, "1.7") < 0) {
      new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                       "Maven 3.3.1+ requires JDK 1.7+. Please set appropriate JDK at <br>" +
                       sdkConfigLocation,
                       NotificationType.WARNING).notify(null);
    }
  }

  public static File getMavenEventListener() {
    return BundledMavenPathHolder.eventListenerJar;
  }

  public static File getMavenLibDirectory() {
    return new File(getInstance().getCurrentMavenHomeFile(), "lib");
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
  public String getCurrentMavenVersion() {
    return getMavenVersion(myState.mavenHome);
  }

  /*
  Made public for external systems intergration
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

  public void createExternalSystemEmbedder(ExternalSystemTaskId id, ExternalSystemTaskNotificationListener listener) {

  }

  public MavenEmbedderWrapper createEmbedder(final Project project,
                                             final boolean alwaysOnline,
                                             @Nullable String workingDirectory,
                                             @Nullable String multiModuleProjectDirectory) {
    return new MavenEmbedderWrapper(this) {
      @NotNull
      @Override
      protected MavenServerEmbedder create() throws RemoteException {
        MavenServerSettings settings = convertSettings(MavenProjectsManager.getInstance(project).getGeneralSettings());
        if (alwaysOnline && settings.isOffline()) {
          settings = settings.clone();
          settings.setOffline(false);
        }

        settings.setProjectJdk(MavenUtil.getSdkPath(ProjectRootManager.getInstance(project).getProjectSdk()));
        return MavenServerManager.this.getOrCreateWrappee()
          .createEmbedder(new MavenEmbedderSettings(settings, workingDirectory, multiModuleProjectDirectory));
      }
    };
  }

  public MavenIndexerWrapper createIndexer() {
    return new MavenIndexerWrapper(this) {
      @NotNull
      @Override
      protected MavenServerIndexer create() throws RemoteException {
        return MavenServerManager.this.getOrCreateWrappee().createIndexer();
      }
    };
  }

  @NotNull
  public MavenModel interpolateAndAlignModel(final MavenModel model, final File basedir) {
    return perform(() -> getOrCreateWrappee().interpolateAndAlignModel(model, basedir));
  }

  public MavenModel assembleInheritance(final MavenModel model, final MavenModel parentModel) {
    return perform(() -> getOrCreateWrappee().assembleInheritance(model, parentModel));
  }

  public ProfileApplicationResult applyProfiles(final MavenModel model,
                                                final File basedir,
                                                final MavenExplicitProfiles explicitProfiles,
                                                final Collection<String> alwaysOnProfiles) {
    return perform(() -> getOrCreateWrappee().applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles));
  }

  public void addDownloadListener(MavenServerDownloadListener listener) {
    myDownloadListener.myListeners.add(listener);
  }

  public void removeDownloadListener(MavenServerDownloadListener listener) {
    myDownloadListener.myListeners.remove(listener);
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

  public static MavenServerConsole wrapAndExport(final MavenConsole console) {
    try {
      RemoteMavenServerConsole result = new RemoteMavenServerConsole(console);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public static MavenServerProgressIndicator wrapAndExport(final MavenProgressIndicator process) {
    try {
      RemoteMavenServerProgressIndicator result = new RemoteMavenServerProgressIndicator(process);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public static MavenServerIndicesProcessor wrapAndExport(final MavenIndicesProcessor processor) {
    try {
      RemoteMavenServerIndicesProcessor result = new RemoteMavenServerIndicesProcessor(processor);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
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

  @TestOnly
  @Deprecated
  public void setUseMaven2() {
    String newMavenHome = BUNDLED_MAVEN_2;
    if (!StringUtil.equals(myState.mavenHome, newMavenHome)) {
      myState.mavenHome = newMavenHome;
      shutdown(false);
    }
  }

  @Nullable
  public static File getMavenHomeFile(@Nullable String mavenHome) {
    if (mavenHome == null) return null;
    //will be removed after IDEA-205421
    if (StringUtil.equals(BUNDLED_MAVEN_2, mavenHome) && ApplicationManager.getApplication().isUnitTestMode()) {
      return BundledMavenPathHolder.myBundledMaven2Home;
    }
    if (StringUtil.equals(BUNDLED_MAVEN_3, mavenHome)) {
      return BundledMavenPathHolder.myBundledMaven3Home;
    }
    final File home = new File(mavenHome);
    return MavenUtil.isValidMavenHome(home) ? home : null;
  }

  @Nullable
  public File getCurrentMavenHomeFile() {
    return getMavenHomeFile(myState.mavenHome);
  }

  public void setMavenHome(@NotNull String mavenHome) {
    if (!StringUtil.equals(myState.mavenHome, mavenHome)) {
      myState.mavenHome = mavenHome;
      shutdown(false);
    }
  }

  @NotNull
  public String getMavenEmbedderVMOptions() {
    return myState.vmOptions;
  }

  public void setMavenEmbedderVMOptions(@NotNull String mavenEmbedderVMOptions) {
    if (!mavenEmbedderVMOptions.trim().equals(myState.vmOptions.trim())) {
      myState.vmOptions = mavenEmbedderVMOptions;
      shutdown(false);
    }
  }

  @NotNull
  public String getEmbedderJdk() {
    return myState.embedderJdk;
  }

  public void setEmbedderJdk(@NotNull String embedderJdk) {
    if (!myState.embedderJdk.equals(embedderJdk)) {
      myState.embedderJdk = embedderJdk;
      shutdown(false);
    }
  }


  @NotNull
  public MavenExecutionOptions.LoggingLevel getLoggingLevel() {
    return myState.loggingLevel;
  }

  public void setLoggingLevel(MavenExecutionOptions.LoggingLevel loggingLevel) {
    if (myState.loggingLevel != loggingLevel) {
      myState.loggingLevel = loggingLevel;
      shutdown(false);
    }
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    if (state.vmOptions == null) {
      state.vmOptions = DEFAULT_VM_OPTIONS;
    }
    if (state.embedderJdk == null) {
      state.embedderJdk = MavenRunnerSettings.USE_INTERNAL_JAVA;
    }
    myState = state;
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

  private static class RemoteMavenServerProgressIndicator extends MavenRemoteObject implements MavenServerProgressIndicator {
    private final MavenProgressIndicator myProcess;

    RemoteMavenServerProgressIndicator(MavenProgressIndicator process) {
      myProcess = process;
    }

    @Override
    public void setText(String text) {
      myProcess.setText(text);
    }

    @Override
    public void setText2(String text) {
      myProcess.setText2(text);
    }

    @Override
    public void startedDownload(ResolveType type, String dependencyId) {
      myProcess.startedDownload(type, dependencyId);
    }

    @Override
    public void completedDownload(ResolveType type, String dependencyId) {
      myProcess.completedDownload(type, dependencyId);
    }

    @Override
    public void failedDownload(ResolveType type, String dependencyId, String errorMessage, String stackTrace) {
      myProcess.failedDownload(type, dependencyId, errorMessage, stackTrace);
    }

    @Override
    public boolean isCanceled() {
      return myProcess.isCanceled();
    }

    @Override
    public void setIndeterminate(boolean value) {
      myProcess.getIndicator().setIndeterminate(value);
    }

    @Override
    public void setFraction(double fraction) {
      myProcess.setFraction(fraction);
    }
  }

  private static class RemoteMavenServerConsole extends MavenRemoteObject implements MavenServerConsole {
    private final MavenConsole myConsole;

    RemoteMavenServerConsole(MavenConsole console) {
      myConsole = console;
    }

    @Override
    public void printMessage(int level, String message, Throwable throwable) {
      myConsole.printMessage(level, message, throwable);
    }
  }

  private static class RemoteMavenServerIndicesProcessor extends MavenRemoteObject implements MavenServerIndicesProcessor {
    private final MavenIndicesProcessor myProcessor;

    private RemoteMavenServerIndicesProcessor(MavenIndicesProcessor processor) {
      myProcessor = processor;
    }

    @Override
    public void processArtifacts(Collection<IndexedMavenId> artifacts) {
      myProcessor.processArtifacts(artifacts);
    }
  }

  public class MavenServerCMDState extends CommandLineState {
    public MavenServerCMDState() {super(null);}

    SimpleJavaParameters createJavaParameters() {
      final SimpleJavaParameters params = new SimpleJavaParameters();

      final Sdk jdk = getJdk();
      params.setJdk(jdk);

      params.setWorkingDirectory(PathManager.getBinPath());


      Map<String, String> defs = new THashMap<>();
      defs.putAll(MavenUtil.getPropertiesFromMavenOpts());

      // pass ssl-related options
      for (Map.Entry<Object, Object> each : System.getProperties().entrySet()) {
        Object key = each.getKey();
        Object value = each.getValue();
        if (key instanceof String && value instanceof String && ((String)key).startsWith("javax.net.ssl")) {
          defs.put((String)key, (String)value);
        }
      }

      defs.put("java.awt.headless", "true");
      for (Map.Entry<String, String> each : defs.entrySet()) {
        params.getVMParametersList().defineProperty(each.getKey(), each.getValue());
      }

      params.getVMParametersList().addProperty("idea.version=", MavenUtil.getIdeaVersionToPassToMavenProcess());

      boolean xmxSet = false;
      boolean maven2Forced = false;

      if (myState.vmOptions != null) {
        ParametersList mavenOptsList = new ParametersList();
        mavenOptsList.addParametersString(myState.vmOptions);

        for (String param : mavenOptsList.getParameters()) {
          if (param.startsWith("-Xmx")) {
            xmxSet = true;
          }
          if (param.equals(FORCE_MAVEN2_OPTION)) {
            MavenLog.LOG.warn("Forced maven 2 option");
            maven2Forced = true;
          }

          params.getVMParametersList().add(param);
        }
      }

      final File mavenHome;
      final String mavenVersion;
      final File currentMavenHomeFile = maven2Forced ? BundledMavenPathHolder.myBundledMaven2Home : getCurrentMavenHomeFile();

      if (currentMavenHomeFile == null) {
        MavenLog.LOG.warn("Not found maven at " + myState.mavenHome);
        mavenHome = BundledMavenPathHolder.myBundledMaven3Home;
        mavenVersion = getMavenVersion(mavenHome);
        showInvalidMavenNotification(mavenVersion);
      }
      else {
        mavenHome = currentMavenHomeFile;
        mavenVersion = getMavenVersion(mavenHome);
      }
      MavenLog.LOG.debug("", currentMavenHomeFile, "with version ", mavenVersion, " chosen as maven home");
      assert mavenVersion != null;

      if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
        params.setMainClass(MAIN_CLASS36);
      }
      else {
        params.setMainClass(MAIN_CLASS);
      }

      params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION, mavenVersion);
      String sdkConfigLocation = "Settings | Build, Execution, Deployment | Build Tools | Maven | Importing | JDK for Importer";
      verifyMavenSdkRequirements(jdk, mavenVersion, sdkConfigLocation);

      final List<String> classPath = new ArrayList<>();
      classPath.add(PathUtil.getJarPathForClass(org.apache.log4j.Logger.class));
      if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
        classPath.add(PathUtil.getJarPathForClass(Logger.class));
        classPath.add(PathUtil.getJarPathForClass(Log4jLoggerFactory.class));
      }

      classPath.add(PathUtil.getJarPathForClass(StringUtilRt.class));//util-rt
      classPath.add(PathUtil.getJarPathForClass(NotNull.class));//annotations-java5
      classPath.add(PathUtil.getJarPathForClass(Element.class));//JDOM
      classPath.add(PathUtil.getJarPathForClass(TIntHashSet.class));//Trove

      ContainerUtil.addIfNotNull(classPath, PathUtil.getJarPathForClass(Query.class));
      params.getClassPath().add(PathManager.getResourceRoot(getClass(), "/messages/CommonBundle.properties"));
      params.getClassPath().addAll(classPath);
      params.getClassPath().addAllFiles(collectClassPathAndLibsFolder(mavenVersion, mavenHome));

      String embedderXmx = System.getProperty("idea.maven.embedder.xmx");
      if (embedderXmx != null) {
        params.getVMParametersList().add("-Xmx" + embedderXmx);
      }
      else {
        if (!xmxSet) {
          params.getVMParametersList().add("-Xmx768m");
        }
      }

      String mavenEmbedderDebugPort = System.getProperty("idea.maven.embedder.debug.port");
      if (mavenEmbedderDebugPort != null) {
        params.getVMParametersList()
          .addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" + mavenEmbedderDebugPort);
      }

      String mavenEmbedderParameters = System.getProperty("idea.maven.embedder.parameters");
      if (mavenEmbedderParameters != null) {
        params.getProgramParametersList().addParametersString(mavenEmbedderParameters);
      }

      String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
      if (mavenEmbedderCliOptions != null) {
        params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS, mavenEmbedderCliOptions);
      }

      MavenUtil.addEventListener(mavenVersion, params);
      return params;
    }

    @NotNull
    @Override
    public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
      ProcessHandler processHandler = startProcess();
      return new DefaultExecutionResult(processHandler);
    }

    @Override
    @NotNull
    protected OSProcessHandler startProcess() throws ExecutionException {
      SimpleJavaParameters params = createJavaParameters();
      GeneralCommandLine commandLine = params.toCommandLine();
      OSProcessHandler processHandler = new OSProcessHandler(commandLine) {
        @NotNull
        @Override
        protected BaseOutputReader.Options readerOptions() {
          return BaseOutputReader.Options.forMostlySilentProcess();
        }
      };
      processHandler.setShouldDestroyProcessRecursively(false);
      return processHandler;
    }

    private void showInvalidMavenNotification(@Nullable String mavenVersion) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      final Project project = openProjects.length == 1 ? openProjects[0] : null;

      String message = messageToShow(myState.mavenHome, mavenVersion, project);

      NotificationListener listener = project == null ? null : new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, MavenSettings.DISPLAY_NAME);
        }
      };

      new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "", message, NotificationType.WARNING, listener).notify(null);
    }

    private String messageToShow(String mavenHome, String mavenVersion, Project project) {
      if (StringUtil.equals(BUNDLED_MAVEN_2, mavenHome)) {
        if (project == null) {
          return RunnerBundle.message("bundled.maven.maven2.not.supported");
        }
        else {
          return RunnerBundle.message("bundled.maven.maven2.not.supported.with.fix");
        }
      }
      else {
        if (project == null) {
          return RunnerBundle.message("external.maven.home.invalid.substitution.warning", myState.mavenHome, mavenVersion);
        }
        else {
          return RunnerBundle.message("external.maven.home.invalid.substitution.warning.with.fix", myState.mavenHome, mavenVersion);
        }
      }
    }
  }
}
