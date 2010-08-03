/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 2, 2005
 * Time: 12:22:07 AM
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.ExtensionPoints;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import com.theoryinpractice.testng.model.IDEARemoteTestRunnerClient;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGRemoteListener;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.ui.TestNGConsoleView;
import com.theoryinpractice.testng.ui.TestNGResults;
import com.theoryinpractice.testng.ui.actions.RerunFailedTestsAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.testng.IDEATestNGListener;
import org.testng.RemoteTestNGStarter;
import org.testng.TestNGCommandLineArgs;
import org.testng.annotations.AfterClass;
import org.testng.remote.strprotocol.MessageHelper;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class TestNGRunnableState extends JavaCommandLineState {
  private static final Logger LOG = Logger.getInstance("TestNG Runner");
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;
  private final TestNGConfiguration config;
  private final RunnerSettings runnerSettings;
  private final IDEARemoteTestRunnerClient client;
  private int port;
  private String debugPort;
  private File myTempFile;
  private BackgroundableProcessIndicator mySearchForTestIndicator;

  public TestNGRunnableState(ExecutionEnvironment environment, TestNGConfiguration config) {
    super(environment);
    this.runnerSettings = environment.getRunnerSettings();
    myConfigurationPerRunnerSettings = environment.getConfigurationSettings();
    this.config = config;
    //TODO need to narrow this down a bit
    //setModulesToCompile(ModuleManager.getInstance(config.getProject()).getModules());
    client = new IDEARemoteTestRunnerClient();
    // Want debugging?
    if (runnerSettings.getData() instanceof DebuggingRunnerData) {
      DebuggingRunnerData debuggingRunnerData = ((DebuggingRunnerData)runnerSettings.getData());
      debugPort = debuggingRunnerData.getDebugPort();
      if (debugPort.length() == 0) {
        try {
          debugPort = DebuggerUtils.getInstance().findAvailableDebugAddress(true);
        }
        catch (ExecutionException e) {
          LOG.error(e);
        }
        debuggingRunnerData.setDebugPort(debugPort);
      }
      debuggingRunnerData.setLocal(true);
    }
  }

  @Override
  public ExecutionResult execute(@NotNull final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
    OSProcessHandler processHandler = null;
    try {
      processHandler = startProcess();
    }
    catch (ExecutionException e) {
      if (mySearchForTestIndicator != null && !mySearchForTestIndicator.isCanceled()) {
        mySearchForTestIndicator.cancel();
      }
      throw e;
    }
    final TestNGConsoleView console = new TestNGConsoleView(config, runnerSettings, myConfigurationPerRunnerSettings);
    console.initUI();
    for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
      ext.handleStartProcess(config, processHandler);
    }
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        client.stopTest();

        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            final Project project = config.getProject();
            if (project.isDisposed()) return;

            final TestConsoleProperties consoleProperties = console.getProperties();
            if (consoleProperties == null) return;
            final String testRunDebugId = consoleProperties.isDebug() ? ToolWindowId.DEBUG : ToolWindowId.RUN;
            final TestNGResults resultsView = console.getResultsView();
            final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            if (!Comparing.strEqual(toolWindowManager.getActiveToolWindowId(), testRunDebugId)) {
              toolWindowManager.notifyByBalloon(testRunDebugId,
                                                resultsView == null || resultsView.getStatus() == MessageHelper.SKIPPED_TEST
                                                ? MessageType.WARNING
                                                : (resultsView.getStatus() == MessageHelper.FAILED_TEST
                                                   ? MessageType.ERROR
                                                   : MessageType.INFO),
                                                resultsView == null ? "Tests were not started" : resultsView.getStatusLine(), null, null);
            }
          }
        });
      }

      @Override
      public void startNotified(final ProcessEvent event) {
        TestNGRemoteListener listener = new TestNGRemoteListener(console);
        client.startListening(listener, listener, port);
      }

      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        final TestNGResults resultsView = console.getResultsView();
        if (resultsView != null) {
          resultsView.finish();
        }
      }

      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        //we override this since we wrap the underlying console, and proxy the attach call,
        //so we never get a chance to intercept the text.
        console.print(event.getText(), ConsoleViewContentType.getConsoleViewType(outputType));
      }
    });
    console.attachToProcess(processHandler);

    RerunFailedTestsAction rerunFailedTestsAction = new RerunFailedTestsAction(console.getComponent());
    rerunFailedTestsAction.init(console.getProperties(), runnerSettings, myConfigurationPerRunnerSettings);
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      public TestFrameworkRunningModel get() {
        return console.getResultsView();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(console, processHandler);
    result.setRestartActions(rerunFailedTestsAction);
    return result;
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final Project project = config.getProject();
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.setupEnvs(config.getPersistantData().getEnvs(), config.getPersistantData().PASS_PARENT_ENVS);
    javaParameters.getVMParametersList().add("-ea");
    javaParameters.setMainClass("org.testng.RemoteTestNGStarter");
    javaParameters.setWorkingDirectory(config.getProperty(RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY));
    javaParameters.getClassPath().add(PathUtil.getJarPathForClass(RemoteTestNGStarter.class));

    //the next few lines are awkward for a reason, using compareTo for some reason causes a JVM class verification error!
    Module module = config.getConfigurationModule().getModule();
    LanguageLevel effectiveLanguageLevel = module == null
                                           ? LanguageLevelProjectExtension.getInstance(project).getLanguageLevel()
                                           : LanguageLevelUtil.getEffectiveLanguageLevel(module);
    final boolean is15 = effectiveLanguageLevel != LanguageLevel.JDK_1_4 && effectiveLanguageLevel != LanguageLevel.JDK_1_3;

    LOG.info("Language level is " + effectiveLanguageLevel.toString());
    LOG.info("is15 is " + is15);

    // Add plugin jars first...
    javaParameters.getClassPath().add(is15 ? PathUtil.getJarPathForClass(AfterClass.class) : //testng-jdk15.jar
                                      new File(PathManager.getPreinstalledPluginsPath(), "testng/lib-jdk14/testng-jdk14.jar")
                                        .getPath());//todo !do not hard code lib name!
    // Configure rest of jars
    JavaParametersUtil.configureConfiguration(javaParameters, config);
    Sdk jdk = module == null ? ProjectRootManager.getInstance(project).getProjectJdk() : ModuleRootManager.getInstance(module).getSdk();
    javaParameters.setJdk(jdk);
    final Object[] patchers = Extensions.getExtensions(ExtensionPoints.JUNIT_PATCHER);
    for (Object patcher : patchers) {
      ((JUnitPatcher)patcher).patchJavaParameters(module, javaParameters);
    }
    JavaSdkUtil.addRtJar(javaParameters.getClassPath());

    // Append coverage parameters if appropriate
    for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
      ext.updateJavaParameters(config, javaParameters, getRunnerSettings());
    }

    LOG.info("Test scope is: " + config.getPersistantData().getScope());
    if (config.getPersistantData().getScope() == TestSearchScope.WHOLE_PROJECT) {
      LOG.info("Configuring for whole project");
      JavaParametersUtil.configureProject(config.getProject(), javaParameters, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                                          config.ALTERNATIVE_JRE_PATH_ENABLED ? config.ALTERNATIVE_JRE_PATH : null);
    }
    else {
      LOG.info("Configuring for module:" + config.getConfigurationModule().getModuleName());
      JavaParametersUtil.configureModule(config.getConfigurationModule(), javaParameters, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                                         config.ALTERNATIVE_JRE_PATH_ENABLED ? config.ALTERNATIVE_JRE_PATH : null);
    }

    try {
      port = NetUtils.findAvailableSocketPort();
    }
    catch (IOException e) {
      throw new ExecutionException("Unable to bind to port " + port, e);
    }

    final TestData data = config.getPersistantData();

    javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.PORT_COMMAND_OPT, String.valueOf(port));

    if (!is15) {
      javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.ANNOTATIONS_COMMAND_OPT, "javadoc");
    }

    if (data.getOutputDirectory() != null && !"".equals(data.getOutputDirectory())) {
      javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.OUTDIR_COMMAND_OPT, data.getOutputDirectory());
    }

    javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.USE_DEFAULT_LISTENERS, String.valueOf(data.USE_DEFAULT_REPORTERS));

    @NonNls final StringBuilder buf = new StringBuilder();
    if (data.TEST_LISTENERS != null && !data.TEST_LISTENERS.isEmpty()) {
      buf.append(StringUtil.join(data.TEST_LISTENERS, ";"));
    }

    for (Object o : Extensions.getExtensions(IDEATestNGListener.EP_NAME)) {
      boolean enabled = true;
      for (RunConfigurationExtension extension : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
        if (extension.isListenerDisabled(config, o)) {
          enabled = false;
          break;
        }
      }
      if (enabled) {
        if (buf.length() > 0) buf.append(";");
        buf.append(o.getClass().getName());
        javaParameters.getClassPath().add(PathUtil.getJarPathForClass(o.getClass()));
      }
    }
    if (buf.length() > 0) javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.LISTENER_COMMAND_OPT, buf.toString());

    // Always include the source paths - just makes things easier :)
    VirtualFile[] sources;
    if ((data.getScope() == TestSearchScope.WHOLE_PROJECT && TestType.PACKAGE.getType().equals(data.TEST_OBJECT)) || module == null) {
      sources = ProjectRootManager.getInstance(project).getContentSourceRoots();
    }
    else {
      sources = ModuleRootManager.getInstance(module).getSourceRoots();
    }

    if (sources.length > 0) {
      StringBuffer sb = new StringBuffer();

      for (int i = 0; i < sources.length; i++) {
        VirtualFile source = sources[i];
        sb.append(source.getPath());
        if (i < sources.length - 1) {
          sb.append(';');
        }

      }

      javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.SRC_COMMAND_OPT, sb.toString());
    }
    try {
      final ServerSocket serverSocket = new ServerSocket(0);
      javaParameters.getProgramParametersList().add("-socket" + serverSocket.getLocalPort());
      myTempFile = File.createTempFile("idea_testng", ".tmp");
      myTempFile.deleteOnExit();
      javaParameters.getProgramParametersList().add("-temp", myTempFile.getAbsolutePath());
      final SearchingForTestsTask task = createSearchingForTestsTask(serverSocket, is15, config, myTempFile);
      mySearchForTestIndicator = new BackgroundableProcessIndicator(task) {
        @Override
        public void cancel() {
          try {//ensure that serverSocket.accept was interrupted
            if (!serverSocket.isClosed()) {
              new Socket(InetAddress.getLocalHost(), serverSocket.getLocalPort());
            }
          }
          catch (Throwable e) {
            LOG.info(e);
          }
          super.cancel();
        }
      };
      ProgressManagerImpl.runProcessWithProgressAsynchronously(task, mySearchForTestIndicator);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    // Configure for debugging
    if (runnerSettings.getData() instanceof DebuggingRunnerData) {
      ParametersList params = javaParameters.getVMParametersList();

      String hostname = "localhost";
      try {
        hostname = InetAddress.getLocalHost().getHostName();
      }
      catch (UnknownHostException e) {
      }
      params.add("-Xdebug");
      params.add("-Xrunjdwp:transport=dt_socket,address=" + hostname + ':' + debugPort + ",suspend=y,server=n");
      //            params.add(debugPort);
    }

    return javaParameters;
  }

  protected SearchingForTestsTask createSearchingForTestsTask(ServerSocket serverSocket, boolean is15,
                                                              final TestNGConfiguration config,
                                                              final File tempFile) {
    return new SearchingForTestsTask(serverSocket, is15, config, tempFile);
  }
}
