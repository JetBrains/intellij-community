// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.theoryinpractice.testng.configuration;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.rt.testng.TestNGXmlSuiteHelper;
import com.intellij.util.ObjectUtils;
import com.intellij.util.execution.ParametersListUtil;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGTestObject;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.testng.xml.LaunchSuite;
import org.testng.xml.Parser;
import org.testng.xml.SuiteGenerator;
import org.testng.xml.XmlSuite;

import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SearchingForTestsTask extends SearchForTestsTask {
  private static final Logger LOG = Logger.getInstance(SearchingForTestsTask.class);
  protected final Map<PsiClass, Map<PsiMethod, List<String>>> myClasses;
  private final TestData myData;
  private final TestNGConfiguration myConfig;
  private final File myTempFile;

  public SearchingForTestsTask(ServerSocket serverSocket,
                               TestNGConfiguration config,
                               File tempFile) {
    super(config.getProject(), serverSocket);
    myData = config.getPersistantData();
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
      FileUtil.writeToFile(myTempFile, "end".getBytes(StandardCharsets.UTF_8), true);
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

  @Override
  protected void logCantRunException(ExecutionException e) {
    try {
      final String message = "CantRunException" + e.getMessage() + "\n";
      FileUtil.writeToFile(myTempFile, message.getBytes(StandardCharsets.UTF_8));
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
            methods.put(method.getName(), Collections.emptyList());
          }
        }
      }
      final String className = ReadAction.compute(() -> ClassUtil.getJVMClassName(entry.getKey()));
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
      properties.load(new ByteArrayInputStream(myConfig.getVMParameters().getBytes(StandardCharsets.UTF_8)));
      final String verbose = properties.getProperty("-Dtestng.verbose");
      if (verbose != null) {
        logLevel = Integer.parseInt(verbose);
      }
    }
    catch (Exception ignore) { //not a number
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
      XmlSuite suite = new XmlSuite();
      suite.setParameters(testParams);
      String programParameters = myConfig.getProgramParameters();
      if (programParameters != null && ParametersListUtil.parse(programParameters).contains("-threadcount")) {
        suite.setThreadCount(-1);
      }
      xmlFile = TestNGXmlSuiteHelper.writeSuite(map, myProject.getName(),
                                                PathManager.getSystemPath(),
                                                new TestNGXmlSuiteHelper.Logger() {
                                                  @Override
                                                  public void log(Throwable e) {
                                                    LOG.error(e);
                                                  }
                                                }, requireToDowngradeToHttp(), suite);
    }
    String path = xmlFile.getAbsolutePath() + "\n";
    try {
      FileUtil.writeToFile(myTempFile, path.getBytes(StandardCharsets.UTF_8), true);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  /**
   * Old testng versions (< 7.0.0) would load dtd from internet iff the dtd is not exactly https://testng.org/testng-1.0.dtd
   * Detect version from manifest is not possible now because manifest doesn't provide this information unfortunately
   */
  private boolean requireToDowngradeToHttp() {
    GlobalSearchScope searchScope = ObjectUtils.notNull(myConfig.getSearchScope(), GlobalSearchScope.allScope(myProject));
    PsiClass testMarker = JavaPsiFacade.getInstance(myProject).findClass(TestNGUtil.TEST_ANNOTATION_FQN, searchScope);
    String version = getVersion(testMarker);
    return version == null || StringUtil.compareVersionNumbers(version, "7.0.0") < 0;
  }

  private static String getVersion(PsiClass classFromCommon) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(classFromCommon);
    if (virtualFile != null) {
      ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(classFromCommon.getProject());
      VirtualFile root = index.getClassRootForFile(virtualFile);
      if (root != null) {
        VirtualFileSystem fileSystem = root.getFileSystem();
        if (fileSystem instanceof JarFileSystem) {
          VirtualFile localFile = ((JarFileSystem)fileSystem).getLocalVirtualFileFor(root);
          if (localFile != null) {
            String name = localFile.getNameWithoutExtension();
            if (name.startsWith("testng-")) {
              return StringUtil.trimStart(name, "testng-");
            }
          }
        }
      }
    }

    return null;
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
        FileUtil.writeToFile(myTempFile, path.getBytes(StandardCharsets.UTF_8), true);
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
        FileWriter fileWriter = new FileWriter(suiteFile, StandardCharsets.UTF_8);
        try {
          fileWriter.write(suite.toXml());
        }
        finally {
          fileWriter.close();
        }
        String path = suiteFile.getAbsolutePath() + "\n";
        FileUtil.writeToFile(myTempFile, path.getBytes(StandardCharsets.UTF_8), true);
      }
    }
    catch (Exception e) {
      LOG.info(e);
      throw new CantRunException(TestngBundle.message("dialog.message.unable.to.parse.suite", e.getMessage()));
    }
  }

  protected void fillTestObjects(final Map<PsiClass, Map<PsiMethod, List<String>>> classes)
    throws CantRunException {
    TestNGTestObject.fromConfig(myConfig).fillTestObjects(classes);
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
          properties.forEach((key, value) -> testParams.put((String)key, (String)value));

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
