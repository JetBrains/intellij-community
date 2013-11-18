/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
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
import gnu.trove.THashMap;
import org.apache.lucene.search.Query;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
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
  storages = {@Storage(
    file = StoragePathMacros.APP_CONFIG + "/mavenVersion.xml")})
public class MavenServerManager extends RemoteObjectWrapper<MavenServer> implements PersistentStateComponent<Element> {
  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.server.RemoteMavenServer";

  private static final String DEFAULT_VM_OPTIONS = "-Xmx512m";

  private final RemoteProcessSupport<Object, MavenServer, Object> mySupport;

  private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
  private final RemoteMavenServerDownloadListener myDownloadListener = new RemoteMavenServerDownloadListener();
  private boolean myLoggerExported;
  private boolean myDownloadListenerExported;

  private final Alarm myShutdownAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private boolean useMaven2 = false;
  private String mavenEmbedderVMOptions = DEFAULT_VM_OPTIONS;
  private String embedderJdk = MavenRunnerSettings.USE_INTERNAL_JAVA;

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
      protected RunProfileState getRunProfileState(Object target, Object configuration, Executor executor) throws ExecutionException {
        return createRunProfileState();
      }
    };

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
        shutdown(false);
      }
    });
  }

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
    if (embedderJdk.equals(MavenRunnerSettings.USE_JAVA_HOME)) {
      final String javaHome = System.getenv("JAVA_HOME");
      if (!StringUtil.isEmptyOrSpaces(javaHome)) {
        Sdk jdk = JavaSdk.getInstance().createJdk("", javaHome);
        if (jdk != null) {
          return jdk;
        }
      }
    }

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(embedderJdk)) {
        return projectJdk;
      }
    }

    // By default use internal jdk
    return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
  }

  private RunProfileState createRunProfileState() {
    return new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {
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

        boolean xmxSet = false;

        if (mavenEmbedderVMOptions != null) {
          ParametersList mavenOptsList = new ParametersList();
          mavenOptsList.addParametersString(mavenEmbedderVMOptions);

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

        return params;
      }

      @Override
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
      }

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

    boolean useMaven2 = this.useMaven2;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      useMaven2 = true;
    }

    if (pluginFileOrDir.isDirectory()) {
      classpath.add(new File(root, "maven-server-api"));

      File luceneLib = new File(PathUtil.getJarPathForClass(Query.class));

      if (useMaven2) {
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

      if (useMaven2) {
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
                                                final Collection<String> explicitProfiles,
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

  public boolean isUseMaven2() {
    return useMaven2;
  }

  public void setUseMaven2(boolean useMaven2) {
    if (this.useMaven2 != useMaven2) {
      this.useMaven2 = useMaven2;
      shutdown(false);
    }
  }

  @NotNull
  public String getMavenEmbedderVMOptions() {
    return mavenEmbedderVMOptions;
  }

  public void setMavenEmbedderVMOptions(@NotNull String mavenEmbedderVMOptions) {
    if (!mavenEmbedderVMOptions.trim().equals(this.mavenEmbedderVMOptions.trim())) {
      this.mavenEmbedderVMOptions = mavenEmbedderVMOptions;
      shutdown(false);
    }
  }

  @NotNull
  public String getEmbedderJdk() {
    return embedderJdk;
  }

  public void setEmbedderJdk(@NotNull String embedderJdk) {
    if (!this.embedderJdk.equals(embedderJdk)) {
      this.embedderJdk = embedderJdk;
      shutdown(false);
    }
  }

  @Nullable
  @Override
  public Element getState() {
    final Element element = new Element("maven-version");
    element.setAttribute("version", useMaven2 ? "2.x" : "3.x");
    element.setAttribute("vmOptions", mavenEmbedderVMOptions);
    return element;
  }

  @Override
  public void loadState(Element state) {
    String version = state.getAttributeValue("version");
    useMaven2 = "2.x".equals(version);

    String vmOptions = state.getAttributeValue("vmOptions");
    mavenEmbedderVMOptions = vmOptions == null ? DEFAULT_VM_OPTIONS : vmOptions;
  }

  private static class RemoteMavenServerLogger extends MavenRemoteObject implements MavenServerLogger {
    public void info(Throwable e) {
      MavenLog.LOG.info(e);
    }

    public void warn(Throwable e) {
      MavenLog.LOG.warn(e);
    }

    public void error(Throwable e) {
      MavenLog.LOG.error(e);
    }

    public void print(String s) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(s);
    }
  }

  private static class RemoteMavenServerDownloadListener extends MavenRemoteObject implements MavenServerDownloadListener {
    private final List<MavenServerDownloadListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

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

    public void setText(String text) {
      myProcess.setText(text);
    }

    public void setText2(String text) {
      myProcess.setText2(text);
    }

    public boolean isCanceled() {
      return myProcess.isCanceled();
    }

    public void setIndeterminate(boolean value) {
      myProcess.getIndicator().setIndeterminate(value);
    }

    public void setFraction(double fraction) {
      myProcess.setFraction(fraction);
    }
  }

  private static class RemoteMavenServerConsole extends MavenRemoteObject implements MavenServerConsole {
    private final MavenConsole myConsole;

    public RemoteMavenServerConsole(MavenConsole console) {
      myConsole = console;
    }

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
