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
package org.jetbrains.idea.maven.facade;

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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.apache.lucene.search.Query;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
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

public class MavenFacadeManager {
  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.facade.RemoteMavenServer";
  private final RemoteProcessSupport<Object, MavenFacade, Object> mySupport;
  private MavenFacade myFacade;
  private RemoteMavenFacadeLogger myLogger;

  public static MavenFacadeManager getInstance() {
    return ServiceManager.getService(MavenFacadeManager.class);
  }

  public MavenFacadeManager() throws RemoteException {
    mySupport = new RemoteProcessSupport<Object, MavenFacade, Object>(MavenFacade.class) {
      @Override
      protected void fireModificationCountChanged() {
      }

      @Override
      protected String getName(Object file) {
        return MavenFacadeManager.class.getSimpleName();
      }

      @Override
      protected RunProfileState getRunProfileState(Object target, Object configuration, Executor executor) throws ExecutionException {
        return createRunProfileState();
      }
    };

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
        mySupport.stopAll();

        if (myLogger != null) {
          try {
            UnicastRemoteObject.unexportObject(myLogger, true);
          }
          catch (RemoteException e) {
            MavenLog.LOG.error(e);
          }
        }
      }
    });
  }

  private synchronized MavenFacade getFacade() {
    if (myFacade == null) {
      try {
        myFacade = mySupport.acquire(this, "");

        myLogger = new RemoteMavenFacadeLogger();
        UnicastRemoteObject.exportObject(myLogger, 0);
        myFacade.setLogger(myLogger);
      }
      catch (Exception e) {
        // todo
        throw new RuntimeException(e);
      }
    }
    return myFacade;
  }


  private RunProfileState createRunProfileState() {
    return new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {
        final SimpleJavaParameters params = new SimpleJavaParameters();

        final Sdk ideaJdk = new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome());
        params.setJdk(ideaJdk);

        params.setWorkingDirectory(PathManager.getBinPath());
        final ArrayList<String> classPath = new ArrayList<String>();
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(NotNull.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(StringUtil.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(THashSet.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Element.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Query.class), classPath);
        params.getClassPath().addAll(classPath);
        addPluginLibraries(params.getClassPath());

        params.setMainClass(MAIN_CLASS);
        params.getVMParametersList().addParametersString("-d32 -Xmx512m");
        return params;
      }

      @Override
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
      }

      protected OSProcessHandler startProcess() throws ExecutionException {
        SimpleJavaParameters params = createJavaParameters();
        Sdk sdk = params.getJdk();

        final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(((JavaSdkType)sdk.getSdkType()).getVMExecutablePath(sdk), params,
                                                                           JdkUtil.useDynamicClasspath(PlatformDataKeys
                                                                                                         .PROJECT.getData(
                                                                               DataManager.getInstance().getDataContext())));
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

  private void addPluginLibraries(PathsList classPath) {
    File pluginFileOrDir = new File(PathUtil.getJarPathForClass(this.getClass()));

    File libDir;

    if (pluginFileOrDir.isDirectory()) {
      classPath.add(new File(pluginFileOrDir.getParent(), "maven-facade-api"));
      classPath.add(new File(pluginFileOrDir.getParent(), "maven-facade-impl"));
      File luceneLib = new File(PathUtil.getJarPathForClass(Query.class));
      libDir = new File(luceneLib.getParentFile().getParentFile().getParentFile(), "facade-impl/lib");
    }
    else {
      libDir = pluginFileOrDir.getParentFile();
    }
    MavenLog.LOG.assertTrue(libDir.exists() && libDir.isDirectory(), "Maven Facade libraries dir not found: " + libDir);

    File[] files = libDir.listFiles();
    for (File jar : files) {
      if (jar.isFile() && jar.getName().endsWith(".jar") && !jar.equals(pluginFileOrDir)) {
        classPath.add(jar.getPath());
      }
    }
  }

  public MavenEmbedderWrapper createEmbedder(Project project) {
    MavenFacade facade = getFacade();
    MavenGeneralSettings settings = MavenProjectsManager.getInstance(project).getGeneralSettings();
    try {
      return new MavenEmbedderWrapper(facade.createEmbedder(convertSettings(settings)));
    }
    catch (RemoteException e) {
      // todo
      throw new RuntimeException(e);
    }
  }

  public MavenIndexerWrapper createIndexer() {
    try {
      return new MavenIndexerWrapper(getFacade().createIndexer());
    }
    catch (RemoteException e) {
      //todo
      throw new RuntimeException(e);
    }
  }

  public List<MavenRepositoryInfo> getRepositories(String nexusUrl) {
    try {
      return getFacade().getRepositories(nexusUrl);
    }
    catch (RemoteException e) {
      // todo
      throw new RuntimeException(e);
    }
  }

  public List<MavenArtifactInfo> findArtifacts(MavenArtifactInfo template, String nexusUrl) {
    try {
      return getFacade().findArtifacts(template, nexusUrl);
    }
    catch (RemoteException e) {
      // todo
      throw new RuntimeException(e);
    }
  }

  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    try {
      return getFacade().interpolateAndAlignModel(model, basedir);
    }
    catch (RemoteException e) {
      // todo
      throw new RuntimeException(e);
    }
  }

  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    try {
      return getFacade().assembleInheritance(model, parentModel);
    }
    catch (RemoteException e) {
      // todo
      throw new RuntimeException(e);
    }
  }

  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                Collection<String> explicitProfiles,
                                                Collection<String> alwaysOnProfiles) {
    try {
      return getFacade().applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
    }
    catch (RemoteException e) {
      // todo
      throw new RuntimeException(e);
    }
  }

  public static MavenFacadeSettings convertSettings(MavenGeneralSettings settings) {
    MavenFacadeSettings result = new MavenFacadeSettings();
    result.setLoggingLevel(settings.getLoggingLevel().getLevel());
    result.setOffline(settings.isWorkOffline());
    result.setMavenHome(settings.getEffectiveMavenHome());
    result.setUserSettingsFile(settings.getEffectiveUserSettingsIoFile());
    result.setGlobalSettingsFile(settings.getEffectiveGlobalSettingsIoFile());
    result.setLocalRepository(settings.getEffectiveLocalRepository());
    result.setPluginUpdatePolicy(settings.getPluginUpdatePolicy().getFacadePolicy());
    result.setSnapshotUpdatePolicy(settings.getSnapshotUpdatePolicy().getFacadePolicy());
    return result;
  }

  public static MavenFacadeConsole wrapandExport(final MavenConsole console) {
    try {
      RemoteMavenFacadeConsole result = new RemoteMavenFacadeConsole(console);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public static MavenFacadeProgressIndicator wrapAndExport(final MavenProgressIndicator process) {
    try {
      RemoteMavenFacadeProgressIndicator result = new RemoteMavenFacadeProgressIndicator(process);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  private static class RemoteMavenFacadeLogger extends RemoteObject implements MavenFacadeLogger {
    public void info(Throwable e) {
      MavenLog.LOG.info(e);
    }

    public void warn(Throwable e) {
      MavenLog.LOG.warn(e);
    }

    public void error(Throwable e) {
      MavenLog.LOG.error(e);
    }
  }

  private static class RemoteMavenFacadeProgressIndicator extends RemoteObject implements MavenFacadeProgressIndicator {
    private final MavenProgressIndicator myProcess;

    public RemoteMavenFacadeProgressIndicator(MavenProgressIndicator process) {
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

  private static class RemoteMavenFacadeConsole extends RemoteObject implements MavenFacadeConsole {
    private final MavenConsole myConsole;

    public RemoteMavenFacadeConsole(MavenConsole console) {
      myConsole = console;
    }

    public void printMessage(int level, String message, Throwable throwable) {
      myConsole.printMessage(level, message, throwable);
    }
  }
}
