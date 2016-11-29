/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGTestObject;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;
import org.testng.TestNGXmlSuiteHelper;
import org.testng.xml.LaunchSuite;
import org.testng.xml.Parser;
import org.testng.xml.SuiteGenerator;
import org.testng.xml.XmlSuite;

import java.io.*;
import java.net.ServerSocket;
import java.util.*;

public class SearchingForTestsTask extends SearchForTestsTask {
  private static final Logger LOG = Logger.getInstance("#" + SearchingForTestsTask.class.getName());
  protected final Map<PsiClass, Map<PsiMethod, List<String>>> myClasses;
  private final TestData myData;
  private final Project myProject;
  private final TestNGConfiguration myConfig;
  private final File myTempFile;

  public SearchingForTestsTask(ServerSocket serverSocket,
                               TestNGConfiguration config,
                               File tempFile) {
    super(config.getProject(), serverSocket);
    myData = config.getPersistantData();
    myProject = config.getProject();
    myConfig = config;
    myTempFile = tempFile;
    myClasses = new LinkedHashMap<>();
  }

  @Override
  protected void onFound() {
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

  @Override
  protected void search() throws CantRunException {
    myClasses.clear();
    fillTestObjects(myClasses);
  }

  protected void logCantRunException(ExecutionException e) {
    try {
      final String message = "CantRunException" + e.getMessage() + "\n";
      FileUtil.writeToFile(myTempFile, message.getBytes());
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  private void composeTestSuiteFromClasses() {
    Map<String, Map<String, List<String>>> map = new LinkedHashMap<>();

    final boolean findTestMethodsForClass = shouldSearchForTestMethods();

    for (final Map.Entry<PsiClass, Map<PsiMethod, List<String>>> entry : myClasses.entrySet()) {
      final Map<PsiMethod, List<String>> depMethods = entry.getValue();
      LinkedHashMap<String, List<String>> methods = new LinkedHashMap<>();
      for (Map.Entry<PsiMethod, List<String>> method : depMethods.entrySet()) {
        methods.put(method.getKey().getName(), method.getValue());
      }
      if (findTestMethodsForClass && depMethods.isEmpty()) {
        for (PsiMethod method : entry.getKey().getMethods()) {
          if (TestNGUtil.hasTest(method)) {
            methods.put(method.getName(), Collections.<String>emptyList());
          }
        }
      }
      final String className = ApplicationManager.getApplication().runReadAction(
        new Computable<String>() {
          @Nullable
          public String compute() {
            return ClassUtil.getJVMClassName(entry.getKey());
          }
        }
      );
      if (className != null) {
        map.put(className, methods);
      }
    }
    // We have groups we wish to limit to.
    Collection<String> groupNames = myConfig.calculateGroupNames();

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

    File xmlFile;
    if (groupNames != null) {
      final LinkedHashMap<String, Collection<String>> methodNames = new LinkedHashMap<>();
      for (Map.Entry<String, Map<String, List<String>>> entry : map.entrySet()) {
        methodNames.put(entry.getKey(), entry.getValue().keySet());
      }
      LaunchSuite suite =
        SuiteGenerator.createSuite(myProject.getName(), null, methodNames, groupNames, testParams, "jdk", logLevel);
      xmlFile = suite.save(new File(PathManager.getSystemPath()));
    }
    else {
      xmlFile = TestNGXmlSuiteHelper.writeSuite(map, testParams, myProject.getName(), 
                                                PathManager.getSystemPath(),
                                                new TestNGXmlSuiteHelper.Logger() {
                                                  @Override
                                                  public void log(Throwable e) {
                                                    LOG.error(e);
                                                  }
                                                });
    }
    String path = xmlFile.getAbsolutePath() + "\n";
    try {
      FileUtil.writeToFile(myTempFile, path.getBytes(CharsetToolkit.UTF8_CHARSET), true);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private boolean shouldSearchForTestMethods() {
    for (Map<PsiMethod, List<String>> methods : myClasses.values()) {
      if (!methods.isEmpty()) {
        return true;
      }
    }
    return false;
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

  protected void fillTestObjects(final Map<PsiClass, Map<PsiMethod, List<String>>> classes)
    throws CantRunException {
    final TestNGTestObject testObject = TestNGTestObject.fromConfig(myConfig);
    if (testObject != null) {
      testObject.fillTestObjects(classes);
    }
  }

  private Map<String, String> buildTestParameters() {
    Map<String, String> testParams = new HashMap<>();

    // Override with those from the test runner configuration
    if (myData.PROPERTIES_FILE != null) {
      File propertiesFile = new File(myData.PROPERTIES_FILE);
      if (propertiesFile.exists()) {

        Properties properties = new Properties();
        try {
          properties.load(new FileInputStream(propertiesFile));
          for (Map.Entry entry : properties.entrySet()) {
            testParams.put((String)entry.getKey(), (String)entry.getValue());
          }

        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    testParams.putAll(myData.TEST_PROPERTIES);
    return testParams;
  }
}
