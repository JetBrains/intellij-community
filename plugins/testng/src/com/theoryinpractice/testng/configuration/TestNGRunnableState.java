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
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import com.theoryinpractice.testng.model.*;
import com.theoryinpractice.testng.ui.TestNGConsoleView;
import com.theoryinpractice.testng.ui.TestNGResults;
import com.theoryinpractice.testng.ui.actions.RerunFailedTestsAction;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.testng.CommandLineArgs;
import org.testng.IDEATestNGListener;
import org.testng.RemoteTestNGStarter;
import org.testng.annotations.AfterClass;
import org.testng.remote.RemoteArgs;
import org.testng.remote.RemoteTestNG;
import org.testng.remote.strprotocol.SerializedMessageSender;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

public class TestNGRunnableState extends JavaCommandLineState {
  private static final Logger LOG = Logger.getInstance("TestNG Runner");
  private static final String TESTNG_TEST_FRAMEWORK_NAME = "TestNG";
  private final TestNGConfiguration config;
  private final RunnerSettings runnerSettings;
  protected final IDEARemoteTestRunnerClient client;
  private int port;
  private String debugPort;
  private File myTempFile;
  private BackgroundableProcessIndicator mySearchForTestIndicator;
  private ServerSocket myServerSocket;

  public TestNGRunnableState(ExecutionEnvironment environment, TestNGConfiguration config) {
    super(environment);
    this.runnerSettings = environment.getRunnerSettings();
    this.config = config;
    //TODO need to narrow this down a bit
    //setModulesToCompile(ModuleManager.getInstance(config.getProject()).getModules());
    client = new IDEARemoteTestRunnerClient();
    // Want debugging?
    if (runnerSettings instanceof DebuggingRunnerData) {
      DebuggingRunnerData debuggingRunnerData = ((DebuggingRunnerData)runnerSettings);
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
    final boolean smRunner = Registry.is("testng_sm_runner", false);
    if (smRunner) {
      return startSMRunner(executor);
    }
    OSProcessHandler processHandler = startProcess();
    final TreeRootNode unboundOutputRoot = new TreeRootNode();
    final TestNGConsoleView console = new TestNGConsoleView(config, getEnvironment(), unboundOutputRoot, executor);
    console.initUI();
    unboundOutputRoot.setPrinter(console.getPrinter());
    Disposer.register(console, unboundOutputRoot);
    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(config, processHandler, runnerSettings);
    final SearchingForTestsTask task = createSearchingForTestsTask(myServerSocket, config, myTempFile);
    processHandler.addProcessListener(new ProcessAdapter() {
      private boolean myStarted = false;

      @Override
      public void processTerminated(final ProcessEvent event) {
        unboundOutputRoot.flush();

        if (mySearchForTestIndicator != null && !mySearchForTestIndicator.isCanceled()) {
          task.finish();
        }

        final Runnable notificationRunnable = new Runnable() {
          public void run() {
            final Project project = config.getProject();
            if (project.isDisposed()) return;

            final TestConsoleProperties consoleProperties = console.getProperties();
            if (consoleProperties == null) return;
            final TestNGResults resultsView = console.getResultsView();
            TestsUIUtil.notifyByBalloon(project, myStarted, console.getResultsView().getRoot(), consoleProperties, "in " + resultsView.getTime());
          }
        };
        SwingUtilities.invokeLater(notificationRunnable);
      }

      @Override
      public void startNotified(final ProcessEvent event) {
        TestNGRemoteListener listener = new TestNGRemoteListener(console, unboundOutputRoot);
        if (config.isSaveOutputToFile()) {
          unboundOutputRoot.setOutputFilePath(config.getOutputFilePath());
        }
        client.prepareListening(listener, port);
        myStarted = true;
        mySearchForTestIndicator = new BackgroundableProcessIndicator(task);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, mySearchForTestIndicator);
      }

      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        final TestNGResults resultsView = console.getResultsView();
        if (resultsView != null) {
          resultsView.finish();
        }
      }

      private int myInsertIndex = 0;
      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        final TestProxy currentTest = console.getCurrentTest();
        final String text = event.getText();
        final ConsoleViewContentType consoleViewType = ConsoleViewContentType.getConsoleViewType(outputType);
        final Printable printable = new Printable() {
          public void printOn(final Printer printer) {
            printer.print(text, consoleViewType);
          }
        };
        if (currentTest != null) {
          currentTest.addLast(printable);
        }
        else {
          unboundOutputRoot.insert(printable, myInsertIndex);
        }
        myInsertIndex++;
      }
    });
    console.attachToProcess(processHandler);

    RerunFailedTestsAction rerunFailedTestsAction = new RerunFailedTestsAction(console);
    rerunFailedTestsAction.init(console.getProperties(), getEnvironment());
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      public TestFrameworkRunningModel get() {
        return console.getResultsView();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(console, processHandler);
    result.setRestartActions(rerunFailedTestsAction);
    return result;
  }

  private ExecutionResult startSMRunner(Executor executor) throws ExecutionException {
    getJavaParameters().getVMParametersList().add("-Didea.testng.sm_runner");
    getJavaParameters().getClassPath().add(PathUtil.getJarPathForClass(ServiceMessageTypes.class));

    OSProcessHandler handler = startProcess();
    TestConsoleProperties testConsoleProperties = new SMTRunnerConsoleProperties(config, TESTNG_TEST_FRAMEWORK_NAME, executor);

    testConsoleProperties.setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false);

    final BaseTestsOutputConsoleView smtConsoleView = SMTestRunnerConnectionUtil.createConsoleWithCustomLocator(
      TESTNG_TEST_FRAMEWORK_NAME,
      testConsoleProperties,
      getEnvironment(), null);


    Disposer.register(getEnvironment().getProject(), smtConsoleView);
    smtConsoleView.attachToProcess(handler);
    final RerunFailedTestsAction rerunFailedTestsAction = new RerunFailedTestsAction(smtConsoleView);
    rerunFailedTestsAction.init(testConsoleProperties, getEnvironment());
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      @Override
      public TestFrameworkRunningModel get() {
        return ((SMTRunnerConsoleView)smtConsoleView).getResultsViewer();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(smtConsoleView, handler);
    result.setRestartActions(rerunFailedTestsAction);

    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(config, handler, runnerSettings);
    final SearchingForTestsTask task = createSearchingForTestsTask(myServerSocket, config, myTempFile);
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {

        if (mySearchForTestIndicator != null && !mySearchForTestIndicator.isCanceled()) {
          task.finish();
        }
      }

      @Override
      public void startNotified(final ProcessEvent event) {
        mySearchForTestIndicator = new BackgroundableProcessIndicator(task);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, mySearchForTestIndicator);
      }
    });

    return result;
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final Project project = config.getProject();
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.setupEnvs(config.getPersistantData().getEnvs(), config.getPersistantData().PASS_PARENT_ENVS);
    javaParameters.setMainClass("org.testng.RemoteTestNGStarter");
    javaParameters.setWorkingDirectory(config.getWorkingDirectory());
    javaParameters.getClassPath().add(PathUtil.getJarPathForClass(RemoteTestNGStarter.class));

    //the next few lines are awkward for a reason, using compareTo for some reason causes a JVM class verification error!
    Module module = config.getConfigurationModule().getModule();
    LanguageLevel effectiveLanguageLevel = module == null
                                           ? LanguageLevelProjectExtension.getInstance(project).getLanguageLevel()
                                           : LanguageLevelUtil.getEffectiveLanguageLevel(module);
    final boolean is15 = effectiveLanguageLevel != LanguageLevel.JDK_1_4 && effectiveLanguageLevel != LanguageLevel.JDK_1_3;

    LOG.info("Language level is " + effectiveLanguageLevel.toString());
    LOG.info("is15 is " + is15);
    final String pathToBundledJar = PathUtil.getJarPathForClass(AfterClass.class);

    // Configure rest of jars
    JavaParametersUtil.configureConfiguration(javaParameters, config);
    Sdk jdk = module == null ? ProjectRootManager.getInstance(project).getProjectSdk() : ModuleRootManager.getInstance(module).getSdk();
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

    javaParameters.getClassPath().add(pathToBundledJar);

    try {
      port = NetUtils.findAvailableSocketPort();
    }
    catch (IOException e) {
      throw new ExecutionException("Unable to bind to port " + port, e);
    }

    final TestData data = config.getPersistantData();

    javaParameters.getProgramParametersList().add(supportSerializationProtocol(config) ? RemoteArgs.PORT : CommandLineArgs.PORT, String.valueOf(port));

    if (data.getOutputDirectory() != null && !"".equals(data.getOutputDirectory())) {
      javaParameters.getProgramParametersList().add(CommandLineArgs.OUTPUT_DIRECTORY, data.getOutputDirectory());
    }

    javaParameters.getProgramParametersList().add(CommandLineArgs.USE_DEFAULT_LISTENERS, String.valueOf(data.USE_DEFAULT_REPORTERS));

    @NonNls final StringBuilder buf = new StringBuilder();
    if (data.TEST_LISTENERS != null && !data.TEST_LISTENERS.isEmpty()) {
      buf.append(StringUtil.join(data.TEST_LISTENERS, ";"));
    }

    for (Object o : Extensions.getExtensions(IDEATestNGListener.EP_NAME)) {
      boolean enabled = true;
      for (RunConfigurationExtension extension : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
        if (extension.isListenerDisabled(config, o, getRunnerSettings())) {
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
    if (buf.length() > 0) javaParameters.getProgramParametersList().add(CommandLineArgs.LISTENER, buf.toString());

   /* // Always include the source paths - just makes things easier :)
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
    }*/
    try {
      myServerSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
      javaParameters.getProgramParametersList().add("-socket" + myServerSocket.getLocalPort());
      myTempFile = FileUtil.createTempFile("idea_testng", ".tmp");
      myTempFile.deleteOnExit();
      javaParameters.getProgramParametersList().add("-temp", myTempFile.getAbsolutePath());
    }
    catch (IOException e) {
      LOG.error(e);
    }
    // Configure for debugging
    if (runnerSettings instanceof DebuggingRunnerData) {
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

  protected SearchingForTestsTask createSearchingForTestsTask(ServerSocket serverSocket,
                                                              final TestNGConfiguration config,
                                                              final File tempFile) {
    return new SearchingForTestsTask(serverSocket, config, tempFile, client);
  }

  public static boolean supportSerializationProtocol(TestNGConfiguration config) {
    final Project project = config.getProject();
    final GlobalSearchScope scopeToDetermineTestngIn;
    if (config.getPersistantData().getScope() == TestSearchScope.WHOLE_PROJECT) {
      scopeToDetermineTestngIn = GlobalSearchScope.allScope(project);
    }
    else {
      scopeToDetermineTestngIn = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(config.getConfigurationModule().getModule());
    }

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass aClass = facade.findClass(SerializedMessageSender.class.getName(), scopeToDetermineTestngIn);
    if (aClass == null) return false;

    final PsiClass[] starters = facade.findClasses(RemoteTestNG.class.getName(), scopeToDetermineTestngIn);
    for (PsiClass starter : starters) {
      if (starter.findFieldByName("m_serPort", false) == null) {
        LOG.info("Multiple TestNG versions found");
        return false;
      }
    }
    return Registry.is("testng.serialized.protocol.enabled") && !TestNGVersionChecker.isVersionIncompatible(project, scopeToDetermineTestngIn);
  }
}
