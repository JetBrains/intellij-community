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
package org.jetbrains.idea.maven.facade.remote;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenExternalParameters;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * @author Gregory.Shrago
 */
public class MavenFacadeManagerImpl implements MavenFacadeManager {
  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.facade.remote.RemoteMavenServer";

  private final RemoteProcessSupport<Object, MavenFacade, Object> mySupport;

  public MavenFacadeManagerImpl(final Project project) {
    mySupport = new RemoteProcessSupport<Object, MavenFacade, Object>(project, MavenFacade.class) {
      @Override
      protected void fireModificationCountChanged() {
      }

      @Override
      protected String getName(Object file) {
        return file.toString();
      }

      @Override
      protected RunProfileState getRunProfileState(Object project, Object configuration, Executor executor) throws ExecutionException {
        return createRunProfileState();
      }
    };
    Disposer.register(project, new Disposable() {
      public void dispose() {
        mySupport.stopAll();
      }
    });
  }

  public MavenFacade getMavenFacade(@NotNull final Object key) throws Exception {
    return mySupport.acquire(key, "");
  }

  public void releaseMavenFacade(@NotNull Object key) {
    mySupport.release(key, "");
  }

  private RunProfileState createRunProfileState() {
    final CommandLineState state = new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {
        final SimpleJavaParameters params = new SimpleJavaParameters();

        final Project project = mySupport.getProject();
        params.setCharset(EncodingProjectManager.getInstance(project).getDefaultCharset());

        final MavenRunnerSettings runnerSettings = MavenRunner.getInstance(project).getState();
        MavenExternalParameters.configureSimpleJavaParameters(params, new MavenRunnerParameters(),
                                                              MavenProjectsManager.getInstance(project).getGeneralSettings(),
                                                              runnerSettings);


        final Sdk ideaJdk = new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome());
        params.setJdk(ideaJdk);

        params.setWorkingDirectory(PathManager.getBinPath());
        final ArrayList<String> classPath = new ArrayList<String>();
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(StringUtil.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(NotNull.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(XMLStreamException.class), classPath);

        params.getClassPath().addAll(classPath);
        params.setMainClass(MAIN_CLASS);
        params.getClassPath().addFirst(getJarPath(MavenFacadeManagerImpl.this.getClass(), MAIN_CLASS));
        tuneParams(params);
        return params;
      }

      @Override
      public ExecutionResult execute(@NotNull final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
        final ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
      }

      protected OSProcessHandler startProcess() throws ExecutionException {
        final SimpleJavaParameters params = createJavaParameters();
        final Sdk sdk = params.getJdk();
        final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(((JavaSdkType)sdk.getSdkType()).getVMExecutablePath(sdk), params,
                                                                           JdkUtil.useDynamicClasspath(PlatformDataKeys
                                                                             .PROJECT.getData(DataManager.getInstance().getDataContext())));
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
    return state;
  }

  private void tuneParams(SimpleJavaParameters params) {
    final String path = getJarPath(getClass(), MAIN_CLASS);
    File file = new File(path);
    if (file.isDirectory()) {
      params.getClassPath().add(file.getPath());
      file = new File(getJarPath(getClass(), "org.apache.maven.artifact.resolver.ArtifactNotFoundException"));
    }

    if (file.isFile()) {
      final File[] files = file.getParentFile().listFiles();
      for (File jar : files) {
        if (jar.isFile() && jar.getName().endsWith(".jar") && !jar.getName().equals("maven.jar")) {
          params.getClassPath().add(jar.getPath());
        }
      }
    }
  }

  public static String getJarPath(@NotNull final Class<?> context, @NotNull final String mainClassName) {
    final String s = PathManager.getResourceRoot(context, "/" + mainClassName.replace('.', '/') + CommonClassNames.CLASS_FILE_EXTENSION);
    return new File(s).getAbsoluteFile().getAbsolutePath();
  }
}
