/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Attribute;
import gnu.trove.THashMap;
import org.apache.lucene.search.Query;
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

    static {
      final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
      final String root = pluginFileOrDir.getParent();

      if (pluginFileOrDir.isDirectory()) {
        File parentFile = getMavenPluginParentFile();
        myBundledMaven2Home = new File(parentFile, "maven2-server-impl/lib/maven2");
        myBundledMaven3Home = new File(parentFile, "maven3-server-impl/lib/maven3");
      }
      else {
        myBundledMaven2Home = new File(root, "maven2");
        myBundledMaven3Home = new File(root, "maven3");
      }
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
        return createRunProfileState();
      }
    };
  }

  @Override
  public void dispose() {
    shutdown(false);
  }

  @SuppressWarnings("ConstantConditions")
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

  private RunProfileState createRunProfileState() {
    return new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() {
        final SimpleJavaParameters params = new SimpleJavaParameters();

        final Sdk jdk = getJdk();
        params.setJdk(jdk);

        params.setWorkingDirectory(PathManager.getBinPath());

        params.setMainClass(MAIN_CLASS);

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

        if (SystemInfo.isMac) {
          String arch = System.getProperty("sun.arch.data.model");
          if (arch != null) {
            params.getVMParametersList().addParametersString("-d" + arch);
          }
        }

        defs.put("java.awt.headless", "true");
        for (Map.Entry<String, String> each : defs.entrySet()) {
          params.getVMParametersList().defineProperty(each.getKey(), each.getValue());
        }

        params.getVMParametersList().addProperty("idea.version=", MavenUtil.getIdeaVersionToPassToMavenProcess());

        boolean xmxSet = false;

        boolean forceMaven2 = false;
        if (myState.vmOptions != null) {
          ParametersList mavenOptsList = new ParametersList();
          mavenOptsList.addParametersString(myState.vmOptions);

          for (String param : mavenOptsList.getParameters()) {
            if (param.startsWith("-Xmx")) {
              xmxSet = true;
            }
            if (param.equals(FORCE_MAVEN2_OPTION)) {
              forceMaven2 = true;
            }

            params.getVMParametersList().add(param);
          }
        }

        final File mavenHome;
        final String mavenVersion;
        final File currentMavenHomeFile = forceMaven2 ? BundledMavenPathHolder.myBundledMaven2Home : getCurrentMavenHomeFile();
        if (currentMavenHomeFile == null) {
          mavenHome = BundledMavenPathHolder.myBundledMaven3Home;
          mavenVersion = getMavenVersion(mavenHome);

          Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
          final Project project = openProjects.length == 1 ? openProjects[0] : null;
          if (project != null) {
            new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "", RunnerBundle.message(
              "external.maven.home.invalid.substitution.warning.with.fix", myState.mavenHome, mavenVersion), NotificationType.WARNING,
                             new NotificationListener() {
                               @Override
                               public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                                 ShowSettingsUtil.getInstance().showSettingsDialog(project, MavenSettings.DISPLAY_NAME);
                               }
                             }).notify(null);
          }
          else {
            new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "", RunnerBundle.message(
              "external.maven.home.invalid.substitution.warning", myState.mavenHome, mavenVersion), NotificationType.WARNING).notify(null);
          }
        }
        else {
          mavenHome = currentMavenHomeFile;
          mavenVersion = getMavenVersion(mavenHome);
        }
        assert mavenVersion != null;

        params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION, mavenVersion);
        String sdkConfigLocation = "Settings | Build, Execution, Deployment | Build Tools | Maven | Importing | JDK for Importer";
        verifyMavenSdkRequirements(jdk, mavenVersion, sdkConfigLocation);

        final List<String> classPath = new ArrayList<>();
        classPath.add(PathUtil.getJarPathForClass(org.apache.log4j.Logger.class));
        if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
          classPath.add(PathUtil.getJarPathForClass(Logger.class));
          classPath.add(PathUtil.getJarPathForClass(Log4jLoggerFactory.class));
        }

        classPath.addAll(PathManager.getUtilClassPath());
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
          params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" + mavenEmbedderDebugPort);
        }

        String mavenEmbedderParameters = System.getProperty("idea.maven.embedder.parameters");
        if (mavenEmbedderParameters != null) {
          params.getProgramParametersList().addParametersString(mavenEmbedderParameters);
        }

        String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
        if (mavenEmbedderCliOptions != null) {
          params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS, mavenEmbedderCliOptions);
        }

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
        OSProcessHandler processHandler = new OSProcessHandler(commandLine);
        processHandler.setShouldDestroyProcessRecursively(false);
        return processHandler;
      }
    };
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

  private static List<File> collectClassPathAndLibsFolder(@NotNull String mavenVersion, @NotNull File mavenHome) {
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final List<File> classpath = new ArrayList<>();
    final String root = pluginFileOrDir.getParent();

    if (pluginFileOrDir.isDirectory()) {
      classpath.add(new File(root, "maven-server-api"));
      File parentFile = getMavenPluginParentFile();
      if (StringUtil.compareVersionNumbers(mavenVersion, "3") < 0) {
        classpath.add(new File(root, "maven2-server-impl"));
        addDir(classpath, new File(parentFile, "maven2-server-impl/lib"));
        // use bundled maven 2.2.1 for all 2.0.x version (since we use org.apache.maven.project.interpolation.StringSearchModelInterpolator introduced in 2.1.0)
        if (StringUtil.compareVersionNumbers(mavenVersion, "2.1.0") < 0) {
          mavenHome = BundledMavenPathHolder.myBundledMaven2Home;
        }
      }
      else {
        classpath.add(new File(root, "maven3-server-common"));
        addDir(classpath, new File(parentFile, "maven3-server-common/lib"));

        if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
          classpath.add(new File(root, "maven30-server-impl"));
        }
        else {
          classpath.add(new File(root, "maven3-server-impl"));
        }
      }
    }
    else {
      classpath.add(new File(root, "maven-server-api.jar"));

      if (StringUtil.compareVersionNumbers(mavenVersion, "3") < 0) {
        classpath.add(new File(root, "maven2-server-impl.jar"));
        addDir(classpath, new File(root, "maven2-server-lib"));
      }
      else {
        classpath.add(new File(root, "maven3-server-common.jar"));
        addDir(classpath, new File(root, "maven3-server-lib"));

        if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
          classpath.add(new File(root, "maven30-server-impl.jar"));
        }
        else {
          classpath.add(new File(root, "maven3-server-impl.jar"));
        }
      }
    }

    addMavenLibs(classpath, mavenHome);
    return classpath;
  }

  private static File getMavenPluginParentFile() {
    File luceneLib = new File(PathUtil.getJarPathForClass(Query.class));
    return luceneLib.getParentFile().getParentFile().getParentFile();
  }

  private static void addMavenLibs(List<File> classpath, File mavenHome) {
    addDir(classpath, new File(mavenHome, "lib"));
    File bootFolder = new File(mavenHome, "boot");
    File[] classworldsJars = bootFolder.listFiles((dir, name) -> StringUtil.contains(name, "classworlds"));
    if (classworldsJars != null) {
      Collections.addAll(classpath, classworldsJars);
    }
  }

  private static void addDir(List<File> classpath, File dir) {
    File[] files = dir.listFiles();
    if (files == null) return;

    for (File jar : files) {
      if (jar.isFile() && jar.getName().endsWith(".jar")) {
        classpath.add(jar);
      }
    }
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
    result.setSnapshotUpdatePolicy(settings.isAlwaysUpdateSnapshots() ? MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE : MavenServerSettings.UpdatePolicy.DO_NOT_UPDATE);
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

  public boolean isUsedMaven2ForProjectImport() {
    //noinspection deprecation
    return myState.useMaven2;
  }

  public boolean isUseMaven2() {
    final String version = getCurrentMavenVersion();
    return version != null && StringUtil.compareVersionNumbers(version, "3") < 0 && StringUtil.compareVersionNumbers(version, "2") >= 0;
  }

  @TestOnly
  public void setUseMaven2(boolean useMaven2) {
    String newMavenHome = useMaven2 ? BUNDLED_MAVEN_2 : BUNDLED_MAVEN_3;
    if (!StringUtil.equals(myState.mavenHome, newMavenHome)) {
      myState.mavenHome = newMavenHome;
      shutdown(false);
    }
  }

  @Nullable
  public static File getMavenHomeFile(@Nullable String mavenHome) {
    if(mavenHome == null) return null;
    if (StringUtil.equals(BUNDLED_MAVEN_2, mavenHome)) {
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
  public void loadState(State state) {
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

    public RemoteMavenServerProgressIndicator(MavenProgressIndicator process) {
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

    public RemoteMavenServerConsole(MavenConsole console) {
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
}
