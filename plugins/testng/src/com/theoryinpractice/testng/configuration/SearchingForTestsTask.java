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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.model.IDEARemoteTestRunnerClient;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.xml.LaunchSuite;
import org.testng.xml.Parser;
import org.testng.xml.SuiteGenerator;
import org.testng.xml.XmlSuite;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
      try {
        final CantRunException[] ex = new CantRunException[1];
        DumbService.getInstance(myProject).repeatUntilPassesInSmartMode(new Runnable() {
          @Override
          public void run() {
            myClasses.clear();
            try {
              fillTestObjects(myClasses);
            }
            catch (CantRunException e) {
              ex[0] = e;
            }
          }
        });
        if (ex[0] != null) throw ex[0];
      }
      catch (CantRunException e) {
        logCantRunException(e);
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  @Override
  public void onSuccess() {
    writeTempFile();
    finish();

    if (!Registry.is("testng_sm_runner", false)) myClient.startListening(myConfig);
  }

  @Override
  public void onCancel() {
    finish();
  }

  public void finish() {
    DataOutputStream os = null;
    try {
      if (mySocket == null || mySocket.isClosed()) return;
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
            return ClassUtil.getJVMClassName(entry.getKey());
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
      SuiteGenerator.createSuite(myProject.getName(), null, map, groupNames, testParams, "jdk", logLevel);

    File xmlFile = suite.save(new File(PathManager.getSystemPath()));
    String path = xmlFile.getAbsolutePath() + "\n";
    try {
      FileUtil.writeToFile(myTempFile, path.getBytes(CharsetToolkit.UTF8_CHARSET), true);
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
        FileUtil.writeToFile(myTempFile, path.getBytes(CharsetToolkit.UTF8_CHARSET), true);
        return;
      }
      final Parser parser = new Parser(myData.getSuiteName());
      parser.setLoadClasses(false);
      final Collection<XmlSuite> suites = parser.parse();
      for (XmlSuite suite : suites) {
        Map<String, String> params = suite.getParameters();

        params.putAll(buildTestParams);

        final String fileId =
          FileUtil.sanitizeFileName(myProject.getName() + '_' + suite.getName() + '_' + Integer.toHexString(suite.getName().hashCode()) + ".xml");
        final File suiteFile = new File(PathManager.getSystemPath(), fileId);
        FileWriter fileWriter = new FileWriter(suiteFile);
        try {
          fileWriter.write(suite.toXml());
        }
        finally {
          fileWriter.close();
        }
        String path = suiteFile.getAbsolutePath() + "\n";
        FileUtil.writeToFile(myTempFile, path.getBytes(CharsetToolkit.UTF8_CHARSET), true);
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
        calculateDependencies(null, classes, TestNGUtil.getAllTestClasses(filter, false));
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
            return ClassUtil.findPsiClass(psiManager, data.getMainClassName().replace('/', '.'), null, true, getSearchScope());
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
      calculateDependencies(null, classes, psiClass);
    }
    else if (data.TEST_OBJECT.equals(TestType.METHOD.getType())) {
      //it's a method
      final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiClass>() {
          @Nullable
          public PsiClass compute() {
            return ClassUtil.findPsiClass(psiManager, data.getMainClassName().replace('/', '.'), null, true, getSearchScope());
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
      collectTestMethods(classes, psiClass, data.getMethodName());
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
    else if (data.TEST_OBJECT.equals(TestType.PATTERN.getType())) {
      for (final String pattern : data.getPatterns()) {
        final String className;
        final String methodName;
        if (pattern.contains(",")) {
          methodName = StringUtil.getShortName(pattern, ',');
          className = StringUtil.getPackageName(pattern, ',');
        } else {
          className = pattern;
          methodName = null;
        }
        
        final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
          @Nullable
          @Override
          public PsiClass compute() {
            return ClassUtil.findPsiClass(psiManager, className.replace('/', '.'), null, true, getSearchScope());
          }
        });
        if (psiClass != null) {
          final Boolean hasTest = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            @Override
            public Boolean compute() {
              return TestNGUtil.hasTest(psiClass);
            }
          });
          if (hasTest) {
            if (StringUtil.isEmpty(methodName)) {
              calculateDependencies(null, classes, psiClass);
            }
            else {
              collectTestMethods(classes, psiClass, methodName);
            }
          } else {
            throw new CantRunException("No tests found in class " + className);
          }
        }
      }
      if (classes.size() != data.getPatterns().size()) {
        TestSearchScope scope = myConfig.getPersistantData().getScope();
        final List<Pattern> compilePatterns = new ArrayList<Pattern>();
        for (String p : data.getPatterns()) {
          final Pattern compilePattern;
          try {
            compilePattern = Pattern.compile(p);
          }
          catch (PatternSyntaxException e) {
            continue;
          }
          if (compilePattern != null) {
            compilePatterns.add(compilePattern);
          }
        }
        TestClassFilter projectFilter =
          new TestClassFilter(scope.getSourceScope(myConfig).getGlobalSearchScope(), myProject, true, true){
            @Override
            public boolean isAccepted(PsiClass psiClass) {
              if (super.isAccepted(psiClass)) {
                final String qualifiedName = psiClass.getQualifiedName();
                LOG.assertTrue(qualifiedName != null);
                for (Pattern pattern : compilePatterns) {
                  if (pattern.matcher(qualifiedName).matches()) return true;
                }
              }
              return false;
            }
          };
        calculateDependencies(null, classes, TestNGUtil.getAllTestClasses(projectFilter, false));
        if (classes.size() == 0) {
          throw new CantRunException("No tests found in for patterns \"" + StringUtil.join(data.getPatterns(), " || ") + '\"');
        }
      }
    }
  }

  private void collectTestMethods(Map<PsiClass, Collection<PsiMethod>> classes, final PsiClass psiClass, final String methodName) {
    final PsiMethod[] methods = ApplicationManager.getApplication().runReadAction(
      new Computable<PsiMethod[]>() {
        public PsiMethod[] compute() {
          return psiClass.findMethodsByName(methodName, true);
        }
      }
    );
    calculateDependencies(methods, classes, psiClass);
    Collection<PsiMethod> psiMethods = classes.get(psiClass);
    if (psiMethods == null) {
      psiMethods = new LinkedHashSet<PsiMethod>();
      classes.put(psiClass, psiMethods);
    }
    ContainerUtil.addAll(psiMethods, methods);
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

  private void calculateDependencies(PsiMethod[] methods,
                                     final Map<PsiClass, Collection<PsiMethod>> results,
                                     @Nullable final PsiClass... classes) {
    calculateDependencies(methods, results, new LinkedHashSet<PsiMember>(), classes);
  }

  private void calculateDependencies(final PsiMethod[] methods,
                                     final Map<PsiClass, Collection<PsiMethod>> results,
                                     final Set<PsiMember> alreadyMarkedToBeChecked,
                                     @Nullable final PsiClass... classes) {
    if (classes != null && classes.length > 0) {
      final Set<String> groupDependencies = new HashSet<String>();
      TestNGUtil.collectAnnotationValues(groupDependencies, "dependsOnGroups", methods, classes);
      final Set<PsiMember> membersToCheckNow = new LinkedHashSet<PsiMember>();
      if (!groupDependencies.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final Project project = classes[0].getProject();
            final PsiClass testAnnotation =
              JavaPsiFacade.getInstance(project).findClass(TestNGUtil.TEST_ANNOTATION_FQN, GlobalSearchScope.allScope(project));
            LOG.assertTrue(testAnnotation != null);
            for (PsiMember psiMember : AnnotatedMembersSearch.search(testAnnotation, getSearchScope())) {
              final PsiAnnotation annotation = AnnotationUtil.findAnnotation(psiMember, TestNGUtil.TEST_ANNOTATION_FQN);
              if (TestNGUtil.isAnnotatedWithParameter(annotation, "groups", groupDependencies)) {
                if (appendMember(psiMember, alreadyMarkedToBeChecked, results)) {
                  membersToCheckNow.add(psiMember);
                }
              }
            }
          }
        });
      }

      collectDependsOnMethods(results, alreadyMarkedToBeChecked, membersToCheckNow, methods, classes);

      if (methods == null) {
        for (PsiClass c : classes) {
          results.put(c, new LinkedHashSet<PsiMethod>());
        }
      } else {
        for (PsiMember psiMember : membersToCheckNow) {
          PsiClass psiClass;
          PsiMethod[] meths = null;
          if (psiMember instanceof PsiMethod) {
            psiClass = psiMember.getContainingClass();
            meths = new PsiMethod[] {(PsiMethod)psiMember};
          } else {
            psiClass = (PsiClass)psiMember;
          }
          calculateDependencies(meths, results, alreadyMarkedToBeChecked, psiClass);
        }
      }
    }
  }

  private static void collectDependsOnMethods(final Map<PsiClass, Collection<PsiMethod>> results,
                                              final Set<PsiMember> alreadyMarkedToBeChecked,
                                              final Set<PsiMember> membersToCheckNow,
                                              final PsiMethod[] methods,
                                              final PsiClass... classes) {
    final PsiClass[] psiClasses;
    if (methods != null && methods.length > 0) {
      final Set<PsiClass> containingClasses = new HashSet<PsiClass>();
      for (PsiMethod method : methods) {
        containingClasses.add(method.getContainingClass());
      }
      psiClasses = containingClasses.toArray(new PsiClass[containingClasses.size()]);
    } else {
      psiClasses = classes;
    }
    for (final PsiClass containingClass : psiClasses) {
      final Set<String> testMethodDependencies = new HashSet<String>();
      TestNGUtil.collectAnnotationValues(testMethodDependencies, "dependsOnMethods", methods, containingClass);
      if (!testMethodDependencies.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final Project project = containingClass.getProject();
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            for (String dependency : testMethodDependencies) {
              final String className = StringUtil.getPackageName(dependency);
              final String methodName = StringUtil.getShortName(dependency);
              if (StringUtil.isEmpty(className)) {
                checkClassMethods(methodName, containingClass, alreadyMarkedToBeChecked, membersToCheckNow, results);
              }
              else {
                final PsiClass aClass = psiFacade.findClass(className, containingClass.getResolveScope());
                if (aClass != null) {
                  checkClassMethods(methodName, aClass, alreadyMarkedToBeChecked, membersToCheckNow, results);
                }
              }
            }
          }
        });
      }
    }
  }

  private static void checkClassMethods(String methodName,
                                        PsiClass containingClass,
                                        Set<PsiMember> alreadyMarkedToBeChecked,
                                        Set<PsiMember> membersToCheckNow, 
                                        Map<PsiClass, Collection<PsiMethod>> results) {
    final PsiMethod[] psiMethods = containingClass.findMethodsByName(methodName, true);
    for (PsiMethod method : psiMethods) {
      if (AnnotationUtil.isAnnotated(method, TestNGUtil.TEST_ANNOTATION_FQN, false) &&
          appendMember(method, alreadyMarkedToBeChecked, results)) {
        membersToCheckNow.add(method);
      }
    }
  }

  private static boolean appendMember(final PsiMember psiMember,
                                      final Set<PsiMember> underConsideration,
                                      final Map<PsiClass, Collection<PsiMethod>> results) {
    boolean result = false;
    final PsiClass psiClass = psiMember instanceof PsiClass ? ((PsiClass)psiMember) : psiMember.getContainingClass();
    Collection<PsiMethod> psiMethods = results.get(psiClass);
    if (psiMethods == null) {
      psiMethods = new LinkedHashSet<PsiMethod>();
      results.put(psiClass, psiMethods);
      if (psiMember instanceof PsiClass) {
        result = underConsideration.add(psiMember);
      }
    }
    if (psiMember instanceof PsiMethod) {
      final boolean add = psiMethods.add((PsiMethod)psiMember);
      if (add) {
        return underConsideration.add(psiMember);
      }
      return false;
    }
    return result;
  }

  private GlobalSearchScope getSearchScope() {
    final TestData data = myConfig.getPersistantData();
    final Module module = myConfig.getConfigurationModule().getModule();
    return data.TEST_OBJECT.equals(TestType.PACKAGE.getType())
           ? myConfig.getPersistantData().getScope().getSourceScope(myConfig).getGlobalSearchScope()
           : module != null ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.projectScope(myConfig.getProject());
  }

}
