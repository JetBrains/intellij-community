/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.apache.lucene.search.Query;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MavenServerManager extends RemoteObjectWrapper<MavenServer> {
  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.server.RemoteMavenServer";

  private final RemoteProcessSupport<Object, MavenServer, Object> mySupport;

  private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
  private final RemoteMavenServerDownloadListener myDownloadListener = new RemoteMavenServerDownloadListener();
  private boolean myLoggerExported;
  private boolean myDownloadListenerExported;

  private final Alarm myShutdownAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

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

  private RunProfileState createRunProfileState() {
    return new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {
        final SimpleJavaParameters params = new SimpleJavaParameters();

        params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));

        params.setWorkingDirectory(PathManager.getBinPath());
        final ArrayList<String> classPath = new ArrayList<String>();
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(NotNull.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(StringUtil.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(THashSet.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Element.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Query.class), classPath);
        params.getClassPath().add(PathManager.getResourceRoot(getClass(), "/messages/CommonBundle.properties"));
        params.getClassPath().addAll(classPath);
        params.getClassPath().addAllFiles(collectClassPathAndLibsFolder().first);

        params.setMainClass(MAIN_CLASS);

        // todo pass sensible parameters, MAVEN_OPTS?
        if (SystemInfo.isMac) {
          String arch = System.getProperty("sun.arch.data.model");
          if (arch != null) {
            params.getVMParametersList().addParametersString("-d" + arch);
          }
        }
        params.getVMParametersList().addParametersString("-Djava.awt.headless=true -Xmx512m");
        //params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009");
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

        final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(
          ((JavaSdkType)sdk.getSdkType()).getVMExecutablePath(sdk), params,false);
        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
          @Override
          public Charset getCharset() {
            return commandLine.getCharset();
          }
        };
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }
    };
  }

  public static Pair<List<File>, File> collectClassPathAndLibsFolder() {
    File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));

    File libDir;
    List<File> classpath = new SmartList<File>();

    if (pluginFileOrDir.isDirectory()) {
      classpath.add(new File(pluginFileOrDir.getParent(), "maven-server-api"));
      classpath.add(new File(pluginFileOrDir.getParent(), "maven2-server-impl"));
      File luceneLib = new File(PathUtil.getJarPathForClass(Query.class));
      libDir = new File(luceneLib.getParentFile().getParentFile().getParentFile(), "maven2-server-impl/lib");
    }
    else {
      libDir = pluginFileOrDir.getParentFile();
    }
    MavenLog.LOG.assertTrue(libDir.exists() && libDir.isDirectory(), "Maven server libraries dir not found: " + libDir);

    File[] files = libDir.listFiles();
    for (File jar : files) {
      if (jar.isFile() && jar.getName().endsWith(".jar") && !jar.equals(pluginFileOrDir)) {
        classpath.add(jar);
      }
    }
    return Pair.create(classpath, libDir);
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
    result.setLoggingLevel(settings.getLoggingLevel().getLevel());
    result.setOffline(settings.isWorkOffline());
    result.setMavenHome(settings.getEffectiveMavenHome());
    result.setUserSettingsFile(settings.getEffectiveUserSettingsIoFile());
    result.setGlobalSettingsFile(settings.getEffectiveGlobalSettingsIoFile());
    result.setLocalRepository(settings.getEffectiveLocalRepository());
    result.setPluginUpdatePolicy(settings.getPluginUpdatePolicy().getServerPolicy());
    result.setSnapshotUpdatePolicy(settings.getSnapshotUpdatePolicy().getServerPolicy());
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
    private final List<MavenServerDownloadListener> myListeners = ContainerUtil.createEmptyCOWList();

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
}
