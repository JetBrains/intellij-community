/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 2, 2005
 * Time: 12:22:07 AM
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.DefaultCoverageFileProvider;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.TestSearchScope;
import com.intellij.execution.junit2.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import com.theoryinpractice.testng.model.*;
import com.theoryinpractice.testng.util.TestNGUtil;
import com.theoryinpractice.testng.ui.TestNGConsoleView;
import edu.emory.mathcs.backport.java.util.Queue;
import org.jetbrains.annotations.Nullable;
import org.testng.TestNG;
import org.testng.TestNGCommandLineArgs;
import org.testng.annotations.AfterClass;
import org.testng.xml.LaunchSuite;
import org.testng.xml.Parser;
import org.testng.xml.SuiteGenerator;
import org.testng.xml.XmlSuite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.*;

public class TestNGRunnableState extends JavaCommandLineState {
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;
  private TestNGConfiguration config;
  private RunnerSettings runnerSettings;
  private IDEARemoteTestRunnerClient client;
  private int port;
  private String debugPort;
  private CoverageSuite myCurrentCoverageSuite;

  public TestNGRunnableState(RunnerSettings runnerSettings,
                             ConfigurationPerRunnerSettings configurationPerRunnerSettings,
                             TestNGConfiguration config) {
    super(runnerSettings, configurationPerRunnerSettings);
    this.runnerSettings = runnerSettings;
    myConfigurationPerRunnerSettings = configurationPerRunnerSettings;
    this.config = config;
    //TODO need to narrow this down a bit
    setModulesToCompile(ModuleManager.getInstance(config.getProject()).getModules());
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
          LOGGER.error(e);
        }
        debuggingRunnerData.setDebugPort(debugPort);
      }
      debuggingRunnerData.setLocal(true);
    }
  }

  @Override
  public ExecutionResult execute() throws ExecutionException {
    final TestNGConsoleView console = new TestNGConsoleView(config.getProject(), new TestNGConsoleProperties(config), runnerSettings, myConfigurationPerRunnerSettings);
    ProcessHandler processHandler = startProcess();
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        client.stopTest();

        if (myCurrentCoverageSuite != null) {
          CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(config.getProject());
          coverageDataManager.coverageGathered(myCurrentCoverageSuite);
        }
      }

      @Override
      public void startNotified(final ProcessEvent event) {
        TestNGRemoteListener listener = new TestNGRemoteListener(console);
        client.startListening(listener, listener, port);
      }

      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        console.getResultsView().finish();
      }

      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        //we override this since we wrap the underlying console, and proxy the attach call,
        //so we never get a chance to intercept the text.
        console.print(event.getText(), ConsoleViewContentType.getConsoleViewType(outputType));
      }
    });
    console.attachToProcess(processHandler);
    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    JavaParameters javaParameters = new JavaParameters();
    EnvironmentVariablesComponent.setupEnvs(javaParameters, config.getPersistantData().ENV_VARIABLES);
    javaParameters.getVMParametersList().add("-ea");
    javaParameters.setMainClass("org.testng.remote.RemoteTestNG");

    //the next few lines are awkward for a reason, using compareTo for some reason causes a JVM class verification error!
    Module module = config.getConfigurationModule().getModule();
    LanguageLevel effectiveLanguageLevel = module.getEffectiveLanguageLevel();
    boolean is15 = effectiveLanguageLevel != LanguageLevel.JDK_1_4 && effectiveLanguageLevel != LanguageLevel.JDK_1_3;

    LOGGER.info("Language level is " + effectiveLanguageLevel.toString());
    LOGGER.info("is15 is " + is15);

    // Add plugin jars first...
    javaParameters.getClassPath().add(new File(PathManager.getPreinstalledPluginsPath(), "testng/lib/testng.jar").getPath());
    javaParameters.getClassPath().add(is15 ? PathUtil.getJarPathForClass(AfterClass.class) : //testng-jdk15.jar
                                      PathUtil.getJarPathForClass(Queue.class));//testng-jdk14.jar

    // Configure rest of jars
    JavaParametersUtil.configureConfiguration(javaParameters, config);
    Project project = config.getProject();
    ProjectJdk jdk =
      module == null ? ProjectRootManager.getInstance(project).getProjectJdk() : ModuleRootManager.getInstance(module).getJdk();
    javaParameters.setJdk(jdk);
    PathUtilEx.addRtJar(javaParameters.getClassPath());

    // Append coverage parameters if appropriate
    if (config.isCoverageEnabled()) {
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);
      DefaultCoverageFileProvider fileProvider = new DefaultCoverageFileProvider(config.getCoverageFilePath());
      LOGGER.info("Adding coverage data from " + fileProvider.getCoverageDataFilePath());
      myCurrentCoverageSuite = coverageDataManager.addCoverageSuite(config.getGeneratedName() + " Coverage Results", fileProvider,
                                                                    config.getCoveragePatterns(), new Date().getTime(),
                                                                    !config.isMergeDataByDefault());
      LOGGER.info("Added coverage data with name '" + myCurrentCoverageSuite.getPresentableName() + "'");
      config.appendCoverageArgument(javaParameters);
    }

    LOGGER.info("Test scope is: " + config.getPersistantData().getScope());
    if (config.getPersistantData().getScope() == TestSearchScope.WHOLE_PROJECT) {
      LOGGER.info("Configuring for whole project");
      JavaParametersUtil.configureProject(config.getProject(), javaParameters, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                                          config.ALTERNATIVE_JRE_PATH_ENABLED ? config.ALTERNATIVE_JRE_PATH : null);
    }
    else {
      LOGGER.info("Configuring for module:" + config.getConfigurationModule().getModuleName());
      JavaParametersUtil.configureModule(config.getConfigurationModule(), javaParameters, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                                         config.ALTERNATIVE_JRE_PATH_ENABLED ? config.ALTERNATIVE_JRE_PATH : null);
    }
    calculateServerPort();

    TestData data = config.getPersistantData();

    javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.PORT_COMMAND_OPT, String.valueOf(port));

    if (!is15) {
      javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.ANNOTATIONS_COMMAND_OPT, "javadoc");
    }

    if (data.getOutputDirectory() != null && !"".equals(data.getOutputDirectory())) {
      javaParameters.getProgramParametersList().add(TestNGCommandLineArgs.OUTDIR_COMMAND_OPT, data.getOutputDirectory());
    }

    // Always include the source paths - just makes things easier :)
    VirtualFile[] sources;
    if (config.getPersistantData().getScope() == TestSearchScope.WHOLE_PROJECT || module == null) {
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

    Map<PsiClass, Collection<PsiMethod>> classes = new HashMap<PsiClass, Collection<PsiMethod>>();
    if (!fillTestObjects(classes, project)) return null;

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
      for (Map.Entry<PsiClass, Collection<PsiMethod>> entry : classes.entrySet()) {
        Collection<String> methods = new HashSet<String>(entry.getValue().size());
        for (PsiMethod method : entry.getValue()) {
          methods.add(method.getName());
        }
        map.put(entry.getKey().getQualifiedName(), methods);
      }
      // We have groups we wish to limit to.
      Collection<String> groupNames = null;
      if (TestType.GROUP.getType().equals(data.TEST_OBJECT)) {
        String groupName = config.getPersistantData().getGroupName();
        if (groupName != null && groupName.length() > 0) {
          groupNames = new HashSet<String>(1);
          groupNames.add(groupName);
        }
      }

      Map<String, String> testParams = buildTestParameters(data);

      String annotationType = is15 ? TestNG.JDK_ANNOTATION_TYPE : TestNG.JAVADOC_ANNOTATION_TYPE;
      LOGGER.info("Using annotationType of " + annotationType);

      LaunchSuite suite = SuiteGenerator.createSuite(project.getName(), null, map, groupNames, testParams, annotationType, 1);

      File xmlFile = suite.save(new File(PathManager.getSystemPath()));
      javaParameters.getProgramParametersList().add(xmlFile.getAbsolutePath());
    }
    else if (TestType.SUITE.getType().equals(data.TEST_OBJECT)) {
      // Running a suite, make a local copy of the suite and apply our custom parameters to it and run that instead.

      try {
        Collection<XmlSuite> suites = new Parser(data.getSuiteName()).parse();
        for (XmlSuite suite : suites) {
          Map<String, String> params = suite.getParameters();

          params.putAll(buildTestParameters(data));

          //String annotationType = is15 ? TestNG.JDK5_ANNOTATION_TYPE : TestNG.JAVADOC_ANNOTATION_TYPE;
          //LOGGER.info("Using annotationType of " + annotationType);
          //suite.setAnnotations(annotationType);

          final String fileId =
            (project.getName() + '_' + suite.getName() + '_' + Integer.toHexString(suite.getName().hashCode()) + ".xml").replace(' ', '_');
          final File suiteFile = new File(PathManager.getSystemPath(), fileId);
          FileWriter fileWriter = new FileWriter(suiteFile);
          fileWriter.write(suite.toXml());
          fileWriter.close();

          javaParameters.getProgramParametersList().add(suiteFile.getAbsolutePath());
        }

      }
      catch (Exception e) {
        ExecutionUtil.showExecutionErrorMessage(new CantRunException("Unable to parse suite: " + e.getMessage()),
                                                "Can't Run " + config.getName(), project);
        return null;
      }
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

  protected boolean fillTestObjects(final Map<PsiClass, Collection<PsiMethod>> classes, final Project project) throws CantRunException {
    final TestData data = config.getPersistantData();
    PsiManager psiManager = PsiManager.getInstance(project);
    if (data.TEST_OBJECT.equals(TestType.PACKAGE.getType())) {
      final String packageName = data.getPackageName();
      PsiPackage psiPackage = psiManager.findPackage(packageName);
      if (psiPackage == null) {
        throw CantRunException.packageNotFound(packageName);
      }
      else {
        TestClassFilter filter = getFilter(psiPackage);
        classes.putAll(calculateDependencies(data, true, TestNGUtil.getAllTestClasses(filter)));
        if (classes.size() == 0) {
          ExecutionUtil.showExecutionErrorMessage(new CantRunException("No tests found in the package \"" + packageName + '\"'),
                                                  "Can't Run " + config.getName(), project);
          return false;
        }
      }
    }
    else if (data.TEST_OBJECT.equals(TestType.CLASS.getType())) {
      //it's a class
      PsiClass psiClass = psiManager.findClass(data.getMainClassName(), data.getScope().getSourceScope(config).getGlobalSearchScope());
      if (psiClass == null) {
        ExecutionUtil.showExecutionErrorMessage(new CantRunException("No tests found in the class \"" + data.getMainClassName() + '\"'),
                                                "Can't Run " + config.getName(), project);
        return false;
      }
      classes.putAll(calculateDependencies(data, true, psiClass));

    }
    else if (data.TEST_OBJECT.equals(TestType.METHOD.getType())) {
      //it's a method
      PsiClass psiClass = psiManager.findClass(data.getMainClassName(), data.getScope().getSourceScope(config).getGlobalSearchScope());
      if (psiClass == null) {
        ExecutionUtil.showExecutionErrorMessage(new CantRunException("No tests found in the class \"" + data.getMainClassName() + '\"'),
                                                "Can't Run " + config.getName(), project);
        return false;
      }
      classes.putAll(calculateDependencies(data, false, psiClass));
      classes.put(psiClass, Arrays.asList(psiClass.findMethodsByName(data.getMethodName(), false)));
    }
    else if (data.TEST_OBJECT.equals(TestType.GROUP.getType())) {
      //for a group, we include all classes
      PsiClass[] testClasses =
        TestNGUtil.getAllTestClasses(new TestClassFilter(data.getScope().getSourceScope(config).getGlobalSearchScope(), project, true));
      for (PsiClass c : testClasses) {
        classes.put(c, new HashSet<PsiMethod>());
      }
    }
    return true;
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
            params.put((String)entry.getKey(), (String)entry.getValue());
          }

        }
        catch (IOException e) {
          LOGGER.error(e);
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
        port = 5000 + (int)(Math.random() * 5000);
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

  private Map<PsiClass, Collection<PsiMethod>> calculateDependencies(TestData data, boolean includeClasses, @Nullable PsiClass... classes) {
    //we build up a list of dependencies
    Map<PsiClass, Collection<PsiMethod>> results = new HashMap<PsiClass, Collection<PsiMethod>>();
    if (classes != null && classes.length > 0) {
      if (includeClasses) {
        for (PsiClass c : classes) {
          results.put(c, new HashSet<PsiMethod>());
        }
      }
      Set<String> dependencies = TestNGUtil.getAnnotationValues("dependsOnGroups", classes);
      if (!dependencies.isEmpty()) {
        PsiManager psiManager = PsiManager.getInstance(classes[0].getProject());
        //we get all classes in the module to figure out which are in the groups we depend on
        PsiClass[] allClasses = psiManager.getSearchHelper().findAllClasses(data.getScope().getSourceScope(config).getGlobalSearchScope());
        Map<PsiClass, Collection<PsiMethod>> filteredClasses = TestNGUtil.filterAnnotations("groups", dependencies, allClasses);
        //we now have a list of dependencies, and a list of classes that match those dependencies
        results.putAll(filteredClasses);
      }
    }
    return results;
  }

  private TestClassFilter getFilter(PsiPackage psiPackage) {
    TestSearchScope scope = config.getPersistantData().getScope();
    //TODO we should narrow this down by module really, if that's what's specified
    TestClassFilter projectFilter = new TestClassFilter(scope.getSourceScope(config).getGlobalSearchScope(), config.getProject(), true);
    return projectFilter.intersectionWith(GlobalSearchScope.packageScope(psiPackage, true));
  }

}
