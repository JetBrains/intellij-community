/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Attribute;
import gnu.trove.THashMap;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@State(
  name = "MavenVersion",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/mavenVersion.xml")
)
public class MavenServerManager extends RemoteObjectWrapper<MavenServer> implements PersistentStateComponent<MavenServerManager.State> {
  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.server.RemoteMavenServer";

  private static final String DEFAULT_VM_OPTIONS = "-Xmx512m";

  private final RemoteProcessSupport<Object, MavenServer, Object> mySupport;

  private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
  private final RemoteMavenServerDownloadListener myDownloadListener = new RemoteMavenServerDownloadListener();
  private boolean myLoggerExported;
  private boolean myDownloadListenerExported;

  private final Alarm myShutdownAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private State myState = new State();

  static class State {
    @Attribute(value = "version", converter = UseMavenConverter.class)
    public boolean useMaven2;
    @Attribute
    public String vmOptions = DEFAULT_VM_OPTIONS;
    @Attribute
    public String embedderJdk = MavenRunnerSettings.USE_INTERNAL_JAVA;
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
      protected String getName(Object file) {
        return MavenServerManager.class.getSimpleName();
      }

      @Override
      protected RunProfileState getRunProfileState(Object target, Object configuration, Executor executor) {
        return createRunProfileState();
      }
    };

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        shutdown(false);
      }
    });
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

    myShutdownAlarm.cancelAllRequests();
  }

  @NotNull
  private Sdk getJdk() {
    if (myState.embedderJdk.equals(MavenRunnerSettings.USE_JAVA_HOME)) {
      final String javaHome = System.getenv("JAVA_HOME");
      if (!StringUtil.isEmptyOrSpaces(javaHome)) {
        Sdk jdk = JavaSdk.getInstance().createJdk("", javaHome);
        if (jdk != null) {
          return jdk;
        }
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

        params.setJdk(getJdk());

        params.setWorkingDirectory(PathManager.getBinPath());
        final List<String> classPath = new ArrayList<String>();
        classPath.addAll(PathManager.getUtilClassPath());
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Query.class), classPath);
        params.getClassPath().add(PathManager.getResourceRoot(getClass(), "/messages/CommonBundle.properties"));
        params.getClassPath().addAll(classPath);
        params.getClassPath().addAllFiles(collectClassPathAndLibsFolder());

        params.setMainClass(MAIN_CLASS);

        Map<String, String> defs = new THashMap<String, String>();
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

        if (myState.vmOptions != null) {
          ParametersList mavenOptsList = new ParametersList();
          mavenOptsList.addParametersString(myState.vmOptions);

          for (String param : mavenOptsList.getParameters()) {
            if (param.startsWith("-Xmx")) {
              xmxSet = true;
            }

            params.getVMParametersList().add(param);
          }
        }

        String embedderXmx = System.getProperty("idea.maven.embedder.xmx");
        if (embedderXmx != null) {
          params.getVMParametersList().add("-Xmx" + embedderXmx);
        }
        else {
          if (!xmxSet) {
            params.getVMParametersList().add("-Xmx512m");
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

        return params;
      }

      @NotNull
      @Override
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
      }

      @Override
      @NotNull
      protected OSProcessHandler startProcess() throws ExecutionException {
        SimpleJavaParameters params = createJavaParameters();
        Sdk sdk = params.getJdk();

        GeneralCommandLine commandLine =
          JdkUtil.setupJVMCommandLine(((JavaSdkType)sdk.getSdkType()).getVMExecutablePath(sdk), params, false);

        OSProcessHandler processHandler =
          new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString(), commandLine.getCharset());

        processHandler.setShouldDestroyProcessRecursively(false);

        return processHandler;
      }
    };
  }

  public static File getMavenLibDirectory() {
    File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    if (pluginFileOrDir.isDirectory()) {
      File luceneLib = new File(PathUtil.getJarPathForClass(Query.class));
      return new File(luceneLib.getParentFile().getParentFile().getParentFile(), "maven3-server-impl/lib/maven3/lib");
    }

    return new File(pluginFileOrDir.getParentFile(), "maven3");
  }

  public List<File> collectClassPathAndLibsFolder() {
    File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));

    List<File> classpath = new ArrayList<File>();

    String root = pluginFileOrDir.getParent();

    if (pluginFileOrDir.isDirectory()) {
      classpath.add(new File(root, "maven-server-api"));

      File luceneLib = new File(PathUtil.getJarPathForClass(Query.class));

      if (myState.useMaven2) {
        classpath.add(new File(root, "maven2-server-impl"));
        addDir(classpath, new File(luceneLib.getParentFile().getParentFile().getParentFile(), "maven2-server-impl/lib"));
      }
      else {
        classpath.add(new File(root, "maven3-server-impl"));

        File maven3Module_Lib = new File(luceneLib.getParentFile().getParentFile().getParentFile(), "maven3-server-impl/lib");
        addDir(classpath, maven3Module_Lib);

        File maven3Home = new File(maven3Module_Lib, "maven3");

        addDir(classpath, new File(maven3Home, "lib"));

        classpath.add(new File(maven3Home, "boot/plexus-classworlds-2.4.jar"));
      }
    }
    else {
      classpath.add(new File(root, "maven-server-api.jar"));

      if (myState.useMaven2) {
        classpath.add(new File(root, "maven2-server-impl.jar"));

        addDir(classpath, new File(root, "maven2"));
      }
      else {
        classpath.add(new File(root, "maven3-server-impl.jar"));
        addDir(classpath, new File(root, "maven3"));
      }
    }

    return classpath;
  }

  private static void addDir(List<File> classpath, File dir) {
    for (File jar : dir.listFiles()) {
      if (jar.isFile() && jar.getName().endsWith(".jar")) {
        classpath.add(jar);
      }
    }
  }

  public MavenEmbedderWrapper createEmbedder(final Project project, final boolean alwaysOnline) {
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

        return MavenServerManager.this.getOrCreateWrappee().createEmbedder(settings);
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

  public MavenModel interpolateAndAlignModel(final MavenModel model, final File basedir) {
    return perform(new Retriable<MavenModel>() {
      @Override
      public MavenModel execute() throws RemoteException {
        return getOrCreateWrappee().interpolateAndAlignModel(model, basedir);
      }
    });
  }

  public MavenModel assembleInheritance(final MavenModel model, final MavenModel parentModel) {
    return perform(new Retriable<MavenModel>() {
      @Override
      public MavenModel execute() throws RemoteException {
        return getOrCreateWrappee().assembleInheritance(model, parentModel);
      }
    });
  }

  public ProfileApplicationResult applyProfiles(final MavenModel model,
                                                final File basedir,
                                                final MavenExplicitProfiles explicitProfiles,
                                                final Collection<String> alwaysOnProfiles) {
    return perform(new Retriable<ProfileApplicationResult>() {
      @Override
      public ProfileApplicationResult execute() throws RemoteException {
        return getOrCreateWrappee().applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
      }
    });
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

  public boolean isUseMaven2() {
    return myState.useMaven2;
  }

  public void setUseMaven2(boolean useMaven2) {
    if (myState.useMaven2 != useMaven2) {
      myState.useMaven2 = useMaven2;
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
      state.vmOptions = MavenRunnerSettings.USE_INTERNAL_JAVA;
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
    public void processArtifacts(Collection<MavenId> artifacts) {
      myProcessor.processArtifacts(artifacts);
    }
  }
}
