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
import com.intellij.codeInsight.AnnotationUtil;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.PathUtil;
import com.theoryinpractice.testng.model.*;
import com.theoryinpractice.testng.ui.TestNGConsoleView;
import com.theoryinpractice.testng.ui.TestNGResults;
import com.theoryinpractice.testng.ui.actions.RerunFailedTestsAction;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.TestNG;
import org.testng.TestNGCommandLineArgs;
import org.testng.IDEATestNGListener;
import org.testng.RemoteTestNGStarter;
import org.testng.annotations.AfterClass;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.xml.LaunchSuite;
import org.testng.xml.Parser;
import org.testng.xml.SuiteGenerator;
import org.testng.xml.XmlSuite;

import javax.swing.*;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.*;

public class TestNGRunnableState extends JavaCommandLineState {
  private static final Logger LOG = Logger.getInstance("TestNG Runner");
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;
  private final TestNGConfiguration config;
  private final RunnerSettings runnerSettings;
  private final IDEARemoteTestRunnerClient client;
  private int port;
  private String debugPort;
  private File myTempFile;

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
    final TestNGConsoleView console = new TestNGConsoleView(config, runnerSettings, myConfigurationPerRunnerSettings);
    console.initUI();
    OSProcessHandler processHandler = startProcess();
    for(RunConfigurationExtension ext: Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
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
                                                  : (resultsView.getStatus() == MessageHelper.FAILED_TEST ? MessageType.ERROR : MessageType.INFO),
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
    calculateServerPort();

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

    final Runnable runnable = new Runnable() {
      public void run() {
        Map<PsiClass, Collection<PsiMethod>> classes = new HashMap<PsiClass, Collection<PsiMethod>>();
        try {
          fillTestObjects(classes, project, is15);


          //if we have testclasses, then we're not running a suite and we have to create one
          //LaunchSuite suite = null;
          //
          //if(testPackage != null) {
          //    List<String> packages = new ArrayList<String>(1);
          //    packages.add(testPackage.getQualifiedName());
          //    suite = SuiteGenerator.createCustomizedSuite(config.project.getName(), packages, null, null, null, data.TEST_PROPERTIES, is15 ? null : "javadoc", 0);
          //} else
          if (classes.size() > 0) {
            Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
            for (final Map.Entry<PsiClass, Collection<PsiMethod>> entry : classes.entrySet()) {
              Collection<String> methods = new HashSet<String>(entry.getValue().size());
              for (PsiMethod method : entry.getValue()) {
                methods.add(method.getName());
              }
              map.put(ApplicationManager.getApplication().runReadAction(
                  new Computable<String>() {
                    @Nullable
                    public String compute() {
                      return entry.getKey().getQualifiedName();
                    }
                  }
              ), methods);
            }
            // We have groups we wish to limit to.
            Collection<String> groupNames = null;
            if (TestType.GROUP.getType().equals(data.TEST_OBJECT)) {
              String groupName = data.getGroupName();
              if (groupName != null && groupName.length() > 0) {
                groupNames = new HashSet<String>(1);
                groupNames.add(groupName);
              }
            }

            Map<String, String> testParams = buildTestParameters(data);

            String annotationType = data.ANNOTATION_TYPE;
            if (annotationType == null || "".equals(annotationType)) {
              annotationType = is15 ? TestNG.JDK_ANNOTATION_TYPE : TestNG.JAVADOC_ANNOTATION_TYPE;
            }

            LOG.info("Using annotationType of " + annotationType);

            int logLevel = 1;
            try {
              final Properties properties = new Properties();
              properties.load(new ByteArrayInputStream(config.getPersistantData().VM_PARAMETERS.getBytes()));
              final String verbose = properties.getProperty("-Dtestng.verbose");
              if (verbose != null) {
                logLevel = Integer.parseInt(verbose);
              }
            }
            catch (Exception e) { //not a number
              logLevel = 1;
            }

            LaunchSuite suite = SuiteGenerator.createSuite(project.getName(), null, map, groupNames, testParams, annotationType, logLevel);

            File xmlFile = suite.save(new File(PathManager.getSystemPath()));
            String path = xmlFile.getAbsolutePath() + "\n";
            try {
              FileUtil.writeToFile(myTempFile, path.getBytes(), true);
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
          else if (TestType.SUITE.getType().equals(data.TEST_OBJECT)) {
            // Running a suite, make a local copy of the suite and apply our custom parameters to it and run that instead.

            try {
              Collection<XmlSuite> suites;
              FileInputStream in = new FileInputStream(data.getSuiteName());
              try {
                suites = new Parser(in).parse();
              }
              finally {
                in.close();
              }
              for (XmlSuite suite : suites) {
                Map<String, String> params = suite.getParameters();

                params.putAll(buildTestParameters(data));

                String annotationType = data.ANNOTATION_TYPE;
                if (annotationType != null && !"".equals(annotationType)) {
                  suite.setAnnotations(annotationType);
                }
                LOG.info("Using annotationType of " + annotationType);

                final String fileId =
                  (project.getName() + '_' + suite.getName() + '_' + Integer.toHexString(suite.getName().hashCode()) + ".xml")
                    .replace(' ', '_');
                final File suiteFile = new File(PathManager.getSystemPath(), fileId);
                FileWriter fileWriter = new FileWriter(suiteFile);
                try {
                  fileWriter.write(suite.toXml());
                }
                finally {
                  fileWriter.close();
                }
                String path = suiteFile.getAbsolutePath() + "\n";
                FileUtil.writeToFile(myTempFile, path.getBytes(), true);
              }
            }
            catch (Exception e) {
              throw new CantRunException("Unable to parse suite: " + e.getMessage());
            }
          }

          try {
            FileUtil.writeToFile(myTempFile, "end".getBytes(), true);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
        catch (CantRunException e) {
          try {
            final String message = "CantRunException" + e.getMessage();
            FileUtil.writeToFile(myTempFile, message.getBytes());
          }
          catch (IOException e1) {
            LOG.error(e1);
          }
        }
      }
    };
    try {
      myTempFile = File.createTempFile("idea_testng", ".tmp");
      myTempFile.deleteOnExit();
      javaParameters.getProgramParametersList().add("-temp", myTempFile.getAbsolutePath());
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching For Tests ...", true) {
        public void run(@NotNull ProgressIndicator indicator) {
          runnable.run();
        }
      });
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

  protected void fillTestObjects(final Map<PsiClass, Collection<PsiMethod>> classes, final Project project, boolean is15) throws CantRunException {
    final TestData data = config.getPersistantData();
    final PsiManager psiManager = PsiManager.getInstance(project);
    if (data.TEST_OBJECT.equals(TestType.PACKAGE.getType())) {
      final String packageName = data.getPackageName();
      PsiPackage psiPackage = ApplicationManager.getApplication().runReadAction(
          new Computable<PsiPackage>() {
            @Nullable
            public PsiPackage compute() {
              return JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(packageName);
            }
          }
      );
      if (psiPackage == null) {
        throw CantRunException.packageNotFound(packageName);
      }
      else {
        TestSearchScope scope = config.getPersistantData().getScope();
        //TODO we should narrow this down by module really, if that's what's specified
        TestClassFilter projectFilter = new TestClassFilter(scope.getSourceScope(config).getGlobalSearchScope(), config.getProject(), true);
        TestClassFilter filter = projectFilter.intersectionWith(PackageScope.packageScope(psiPackage, true));
        classes.putAll(calculateDependencies(null, is15, TestNGUtil.getAllTestClasses(filter, false)));
        if (classes.size() == 0) {
          throw new CantRunException("No tests found in the package \"" + packageName + '\"');
        }
      }
    }
    else if (data.TEST_OBJECT.equals(TestType.CLASS.getType())) {
      //it's a class
      final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(
          new Computable<PsiClass>() {
            @Nullable
            public PsiClass compute() {
              return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(data.getMainClassName(), getSearchScope());
            }
          }
      );
      if (psiClass == null) {
        throw new CantRunException("No tests found in the class \"" + data.getMainClassName() + '\"');
      }
      if (null == ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        public String compute() {
          return psiClass.getQualifiedName();
        }
      })) {
        throw new CantRunException("Cannot test anonymous or local class \"" + data.getMainClassName() + '\"');
      }
      classes.putAll(calculateDependencies(null, is15, psiClass));
    }
    else if (data.TEST_OBJECT.equals(TestType.METHOD.getType())) {
      //it's a method
      final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(
          new Computable<PsiClass>() {
            @Nullable
            public PsiClass compute() {
              return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(data.getMainClassName(), getSearchScope());
            }
          }
      );
      if (psiClass == null) {
        throw new CantRunException("No tests found in the class \"" + data.getMainClassName() + '\"');
      }
      if (null == ApplicationManager.getApplication().runReadAction(
          new Computable<String>() {
            @Nullable
            public String compute() {
              return psiClass.getQualifiedName();
            }
          }
      )) {
        throw new CantRunException("Cannot test anonymous or local class \"" + data.getMainClassName() + '\"');
      }
      final PsiMethod[] methods = ApplicationManager.getApplication().runReadAction(
          new Computable<PsiMethod[]>() {
            public PsiMethod[] compute() {
              return psiClass.findMethodsByName(data.getMethodName(), true);
            }
          }
      );
      classes.putAll(calculateDependencies(methods, is15, psiClass));
      Collection<PsiMethod> psiMethods = classes.get(psiClass);
      if (psiMethods == null) {
        psiMethods = new LinkedHashSet<PsiMethod>();
        classes.put(psiClass, psiMethods);
      }
      psiMethods.addAll(Arrays.asList(methods));
    }
    else if (data.TEST_OBJECT.equals(TestType.GROUP.getType())) {
      //for a group, we include all classes
      PsiClass[] testClasses = TestNGUtil
        .getAllTestClasses(new TestClassFilter(data.getScope().getSourceScope(config).getGlobalSearchScope(), project, true), false);
      for (PsiClass c : testClasses) {
        classes.put(c, new HashSet<PsiMethod>());
      }
    }
  }

  private static Map<String, String> buildTestParameters(TestData data) {
    Map<String, String> testParams = new HashMap<String, String>();

    // Override with those from the test runner configuration
    testParams.putAll(convertPropertiesFileToMap(data.PROPERTIES_FILE));
    testParams.putAll(data.TEST_PROPERTIES);

    return testParams;
  }

  private static Map<String, String> convertPropertiesFileToMap(String properties_file) {
    Map<String, String> params = new HashMap<String, String>();

    if (properties_file != null) {
      File propertiesFile = new File(properties_file);
      if (propertiesFile.exists()) {

        Properties properties = new Properties();
        try {
          properties.load(new FileInputStream(propertiesFile));
          for (Map.Entry entry : properties.entrySet()) {
            params.put((String) entry.getKey(), (String) entry.getValue());
          }

        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return params;
  }

  private void calculateServerPort() throws ExecutionException {
    port = 5000;
    int counter = 0;
    IOException exception = null;
    ServerSocket socket = null;
    while (counter++ < 10) {
      try {
        socket = new ServerSocket(port);
        break;
      }
      catch (IOException ex) {
        //we keep trying
        exception = ex;
        port = 5000 + (int) (Math.random() * 5000);
      }
      finally {
        if (socket != null) {
          try {
            socket.close();
          }
          catch (IOException e) {
          }
        }
      }
    }

    if (socket == null) {
      throw new ExecutionException("Unable to bind to port " + port, exception);
    }
  }

  private Map<PsiClass, Collection<PsiMethod>> calculateDependencies(PsiMethod[] methods, final boolean is15, @Nullable final PsiClass... classes) {
    //we build up a list of dependencies
    final Map<PsiClass, Collection<PsiMethod>> results = new HashMap<PsiClass, Collection<PsiMethod>>();
    if (classes != null && classes.length > 0) {
      final Set<String> dependencies = new HashSet<String>();
      TestNGUtil.collectAnnotationValues(dependencies, "dependsOnGroups", methods, classes);
      ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run(){
              if (!dependencies.isEmpty()) {
              final Project project = classes[0].getProject();
              //we get all classes in the module to figure out which are in the groups we depend on
              Collection<PsiClass> allClasses;
              if (!is15) {
                allClasses = AllClassesSearch.search(getSearchScope(), project).findAll();
                Map<PsiClass, Collection<PsiMethod>> filteredClasses = TestNGUtil.filterAnnotations("groups", dependencies, allClasses);
                //we now have a list of dependencies, and a list of classes that match those dependencies
                results.putAll(filteredClasses);
              }
              else {
                final PsiClass testAnnotation =
                  JavaPsiFacade.getInstance(project).findClass(TestNGUtil.TEST_ANNOTATION_FQN, GlobalSearchScope.allScope(project));
                LOG.assertTrue(testAnnotation != null);
                for (PsiMember psiMember : AnnotatedMembersSearch.search(testAnnotation, getSearchScope())) {
                  if (TestNGUtil
                    .isAnnotatedWithParameter(AnnotationUtil.findAnnotation(psiMember, TestNGUtil.TEST_ANNOTATION_FQN), "groups", dependencies)) {
                    final PsiClass psiClass = psiMember instanceof PsiClass ? ((PsiClass)psiMember) : psiMember.getContainingClass();
                    Collection<PsiMethod> psiMethods = results.get(psiClass);
                    if (psiMethods == null) {
                      psiMethods = new LinkedHashSet<PsiMethod>();
                      results.put(psiClass, psiMethods);
                    }
                    if (psiMember instanceof PsiMethod) {
                      psiMethods.add((PsiMethod)psiMember);
                    }
                  }
                }
              }
            }
          }
      });

      if (methods == null) {
        for (PsiClass c : classes) {
          results.put(c, new LinkedHashSet<PsiMethod>());
        }
      }
    }
    return results;
  }

  private GlobalSearchScope getSearchScope() {
    final TestData data = config.getPersistantData();
    final Module module = config.getConfigurationModule().getModule();
    return data.TEST_OBJECT.equals(TestType.PACKAGE.getType())
           ? config.getPersistantData().getScope().getSourceScope(config).getGlobalSearchScope()
           : module != null ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.projectScope(config.getProject());
  }
}
