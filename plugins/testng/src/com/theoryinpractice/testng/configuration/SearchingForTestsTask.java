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

/*
 * User: anna
 * Date: 28-Apr-2010
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.CantRunException;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.model.IDEARemoteTestRunnerClient;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.internal.AnnotationTypeEnum;
import org.testng.xml.LaunchSuite;
import org.testng.xml.Parser;
import org.testng.xml.SuiteGenerator;
import org.testng.xml.XmlSuite;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class SearchingForTestsTask extends Task.Backgroundable {
  private static final Logger LOG = Logger.getInstance("#" + SearchingForTestsTask.class.getName());
  private final Map<PsiClass, Collection<PsiMethod>> myClasses;
  private Socket mySocket;
  private final TestData myData;
  private final Project myProject;
  private final ServerSocket myServerSocket;
  private final TestNGConfiguration myConfig;
  private final File myTempFile;
  private final IDEARemoteTestRunnerClient myClient;

  public SearchingForTestsTask(ServerSocket serverSocket,
                               TestNGConfiguration config,
                               File tempFile,
                               IDEARemoteTestRunnerClient client) {
    super(config.getProject(), "Searching For Tests ...", true);
    myClient = client;
    myData = config.getPersistantData();
    myProject = config.getProject();
    myServerSocket = serverSocket;
    myConfig = config;
    myTempFile = tempFile;
    myClasses = new HashMap<PsiClass, Collection<PsiMethod>>();
  }

  public void run(@NotNull ProgressIndicator indicator) {
    try {
      mySocket = myServerSocket.accept();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    try {
      fillTestObjects(myClasses);
    }
    catch (CantRunException e) {
      logCantRunException(e);
    }
  }

  @Override
  public void onSuccess() {
    writeTempFile();
    connect();

    myClient.startListening(myConfig);
  }

  @Override
  public void onCancel() {
    connect();
  }

  @Override
  public DumbModeAction getDumbModeAction() {
    return DumbModeAction.WAIT;
  }

  public void connect() {
    DataOutputStream os = null;
    try {
      os = new DataOutputStream(mySocket.getOutputStream());
      os.writeBoolean(true);
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    finally {
      try {
        if (os != null) os.close();
      }
      catch (Throwable e) {
        LOG.info(e);
      }

      try {
        myServerSocket.close();
      }
      catch (Throwable e) {
        LOG.info(e);
      }
    }
  }

  private void writeTempFile() {
    if (myClasses.size() > 0) {
      composeTestSuiteFromClasses();
    }
    else if (TestType.SUITE.getType().equals(myData.TEST_OBJECT)) {
      // Running a suite, make a local copy of the suite and apply our custom parameters to it and run that instead.
      try {
        composeTestSuiteFromXml();
      }
      catch (CantRunException e) {
        logCantRunException(e);
      }
    }

    try {
      FileUtil.writeToFile(myTempFile, "end".getBytes(), true);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void logCantRunException(CantRunException e) {
    try {
      final String message = "CantRunException" + e.getMessage() + "\n";
      FileUtil.writeToFile(myTempFile, message.getBytes());
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  private void composeTestSuiteFromClasses() {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    for (final Map.Entry<PsiClass, Collection<PsiMethod>> entry : myClasses.entrySet()) {
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
    if (TestType.GROUP.getType().equals(myData.TEST_OBJECT)) {
      String groupName = myData.getGroupName();
      if (groupName != null && groupName.length() > 0) {
        groupNames = new HashSet<String>(1);
        groupNames.add(groupName);
      }
    }

    Map<String, String> testParams = buildTestParameters();

    int logLevel = 1;
    try {
      final Properties properties = new Properties();
      properties.load(new ByteArrayInputStream(myConfig.getPersistantData().VM_PARAMETERS.getBytes()));
      final String verbose = properties.getProperty("-Dtestng.verbose");
      if (verbose != null) {
        logLevel = Integer.parseInt(verbose);
      }
    }
    catch (Exception e) { //not a number
      logLevel = 1;
    }

    LaunchSuite suite =
      SuiteGenerator.createSuite(myProject.getName(), null, map, groupNames, testParams, AnnotationTypeEnum.JDK.getName(), logLevel);

    File xmlFile = suite.save(new File(PathManager.getSystemPath()));
    String path = xmlFile.getAbsolutePath() + "\n";
    try {
      FileUtil.writeToFile(myTempFile, path.getBytes(), true);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void composeTestSuiteFromXml() throws CantRunException {
    final Map<String, String> buildTestParams = buildTestParameters();
    try {
      if (buildTestParams.isEmpty()) {
        String path = new File(myData.getSuiteName()).getAbsolutePath() + "\n";
        FileUtil.writeToFile(myTempFile, path.getBytes(), true);
        return;
      }
      Collection<XmlSuite> suites;
      FileInputStream in = new FileInputStream(myData.getSuiteName());
      try {
        suites = new Parser(in).parse();
      }
      finally {
        in.close();
      }

      for (XmlSuite suite : suites) {
        Map<String, String> params = suite.getParameters();

        params.putAll(buildTestParams);

        final String fileId =
          (myProject.getName() + '_' + suite.getName() + '_' + Integer.toHexString(suite.getName().hashCode()) + ".xml")
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

  protected void fillTestObjects(final Map<PsiClass, Collection<PsiMethod>> classes)
    throws CantRunException {
    final TestData data = myConfig.getPersistantData();
    final PsiManager psiManager = PsiManager.getInstance(myProject);
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
        TestSearchScope scope = myConfig.getPersistantData().getScope();
        //TODO we should narrow this down by module really, if that's what's specified
        TestClassFilter projectFilter =
          new TestClassFilter(scope.getSourceScope(myConfig).getGlobalSearchScope(), myProject, true, true);
        TestClassFilter filter = projectFilter.intersectionWith(PackageScope.packageScope(psiPackage, true));
        classes.putAll(calculateDependencies(null, TestNGUtil.getAllTestClasses(filter, false)));
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
      classes.putAll(calculateDependencies(null, psiClass));
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
      classes.putAll(calculateDependencies(methods, psiClass));
      Collection<PsiMethod> psiMethods = classes.get(psiClass);
      if (psiMethods == null) {
        psiMethods = new LinkedHashSet<PsiMethod>();
        classes.put(psiClass, psiMethods);
      }
      ContainerUtil.addAll(psiMethods, methods);
    }
    else if (data.TEST_OBJECT.equals(TestType.GROUP.getType())) {
      //for a group, we include all classes
      PsiClass[] testClasses = TestNGUtil
        .getAllTestClasses(new TestClassFilter(data.getScope().getSourceScope(myConfig).getGlobalSearchScope(), myProject, true, true), false);
      if (testClasses != null) {
        for (PsiClass c : testClasses) {
          classes.put(c, new HashSet<PsiMethod>());
        }
      }
    }
  }

  private Map<String, String> buildTestParameters() {
    Map<String, String> testParams = new HashMap<String, String>();

    // Override with those from the test runner configuration
    testParams.putAll(convertPropertiesFileToMap(myData.PROPERTIES_FILE));
    testParams.putAll(myData.TEST_PROPERTIES);

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
          LOG.error(e);
        }
      }
    }
    return params;
  }

  private Map<PsiClass, Collection<PsiMethod>> calculateDependencies(PsiMethod[] methods,
                                                                     @Nullable final PsiClass... classes) {
    //we build up a list of dependencies
    final Map<PsiClass, Collection<PsiMethod>> results = new HashMap<PsiClass, Collection<PsiMethod>>();
    if (classes != null && classes.length > 0) {
      final Set<String> groupDependencies = new HashSet<String>();
      TestNGUtil.collectAnnotationValues(groupDependencies, "dependsOnGroups", methods, classes);

      final Set<String> testMethodDependencies = new HashSet<String>();
      TestNGUtil.collectAnnotationValues(testMethodDependencies, "dependsOnMethods", methods, classes);

      if (!groupDependencies.isEmpty() || !testMethodDependencies.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final Project project = classes[0].getProject();
            final PsiClass testAnnotation =
              JavaPsiFacade.getInstance(project).findClass(TestNGUtil.TEST_ANNOTATION_FQN, GlobalSearchScope.allScope(project));
            LOG.assertTrue(testAnnotation != null);
            for (PsiMember psiMember : AnnotatedMembersSearch.search(testAnnotation, getSearchScope())) {
              if (psiMember instanceof PsiMethod && testMethodDependencies.contains(psiMember.getName())) {
                appendMember(psiMember, results);
              } else if (!groupDependencies.isEmpty()) {
                final PsiAnnotation annotation = AnnotationUtil.findAnnotation(psiMember, TestNGUtil.TEST_ANNOTATION_FQN);
                if (TestNGUtil.isAnnotatedWithParameter(annotation, "groups", groupDependencies)) {
                  appendMember(psiMember, results);
                }
              }
            }
          }
        });
      }

      if (methods == null) {
        for (PsiClass c : classes) {
          results.put(c, new LinkedHashSet<PsiMethod>());
        }
      }
    }
    return results;
  }

  private static void appendMember(final PsiMember psiMember, final Map<PsiClass, Collection<PsiMethod>> results) {
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

  private GlobalSearchScope getSearchScope() {
    final TestData data = myConfig.getPersistantData();
    final Module module = myConfig.getConfigurationModule().getModule();
    return data.TEST_OBJECT.equals(TestType.PACKAGE.getType())
           ? myConfig.getPersistantData().getScope().getSourceScope(myConfig).getGlobalSearchScope()
           : module != null ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.projectScope(myConfig.getProject());
  }

}
