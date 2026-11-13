// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testng.integration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerTreeStructure;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.util.TestNGUtil;
import jetbrains.buildServer.messages.serviceMessages.BaseTestMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class TestNGIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Parameterized.Parameter
  public String myTestNGVersion;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
      //createParams("5.14.10"), // the last version of 5.x branch (Feb 15, 2011), still used in some projects
      createParams("6.8"),
      createParams("6.9.10"), // the popular version
      createParams("6.11"),
      createParams("6.10"),
      createParams("6.13.1"),
      createParams("6.14.3"), // the last version of 6.x branch (Apr 09, 2018), still used in some projects
      createParams("7.12.0")
    );
  }

  private static Object[] createParams(final String version) {
    return new Object[]{version};
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.testng", "testng", myTestNGVersion), getRepoManager());
    assertNotNull("Test annotation not found", 
                  JavaPsiFacade.getInstance(getProject())
                               .findClass(TestNGUtil.TEST_ANNOTATION_FQN, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));
  }

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/testng_rt/tests/testData/integration/" + getName());
  }

  @Test
  public void simpleStart() throws ExecutionException {
    PsiClass psiClass = findClass(myModule, "a.Test1");
    assertNotNull(psiClass);
    TestNGConfiguration configuration = createConfiguration(psiClass);
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    String testOutput = processOutput.out.toString();
    assertEmpty(ContainerUtil.filter(processOutput.err, s -> !s.startsWith("SLF4J(W): ")));
    assertTrue(testOutput, testOutput.contains("sample output"));

    boolean supportedDisplayName = !myTestNGVersion.startsWith("5");
    String testName = supportedDisplayName ? "Test1.myName" : "Test1.simple";

    String messages = processOutput.messages.stream()
      .filter(m -> m instanceof BaseTestMessage)
      .map(m -> m.asString())
      .map(s -> s.replaceAll(" duration='(.*?)'", ""))
      .collect(Collectors.joining("\n"));

    assertEquals("""
                   ##teamcity[testStarted name='%s' nodeId='a.Test1/simple' parentNodeId='a.Test1' locationHint='java:test://a.Test1/simple']
                   ##teamcity[testFinished name='%s' nodeId='a.Test1/simple']
                   ##teamcity[testStarted name='Test1.simple2' nodeId='a.Test1/simple2' parentNodeId='a.Test1' locationHint='java:test://a.Test1/simple2']
                   ##teamcity[testFinished name='Test1.simple2' nodeId='a.Test1/simple2']""".formatted(testName, testName), messages);
  }

  @Test
  public void configurationMethods() throws ExecutionException {
    PsiClass psiClass = findClass(myModule, "a.ConfigTest");
    assertNotNull(psiClass);
    TestNGConfiguration configuration = createConfiguration(psiClass);
    ProcessOutput processOutput = doStartTestsProcess(configuration);

    SMTRunnerConsoleProperties properties = configuration.createTestConsoleProperties(DefaultRunExecutor.getRunExecutorInstance());
    boolean hidePassedTests = TestConsoleProperties.HIDE_PASSED_TESTS.value(properties);
    boolean hideSuccessfulConfig = TestConsoleProperties.HIDE_SUCCESSFUL_CONFIG.value(properties);
    TestConsoleProperties.HIDE_PASSED_TESTS.set(properties, false);
    TestConsoleProperties.HIDE_SUCCESSFUL_CONFIG.set(properties, true);
    try {
      SMTRunnerConsoleView console = (SMTRunnerConsoleView)SMTestRunnerConnectionUtil.createConsole("TestNG", properties);
      Disposer.register(getTestRootDisposable(), console);
      ProcessHandler processHandler = new NopProcessHandler();
      console.attachToProcess(processHandler);
      processHandler.startNotify();
      for (ServiceMessage message : processOutput.messages) {
        processHandler.notifyTextAvailable(message.asString() + "\n", ProcessOutputTypes.STDOUT);
      }
      processHandler.destroyProcess();

      SMTestRunnerResultsForm resultsViewer = console.getResultsViewer();
      SMTestProxy root = resultsViewer.getTestsRootNode();
      List<String> visibleTests = new ArrayList<>();
      collectVisibleTests(resultsViewer.getTreeBuilder().getTreeStructure(), root, visibleTests);
      assertEquals("visible tests: " + visibleTests, 2, resultsViewer.getFinishedTestCount());
      assertEquals(List.of("ConfigTest.test1", "ConfigTest.test2"), visibleTests);
      assertEquals(List.of("ConfigTest.beforeClass",
                           "ConfigTest.beforeMethod", "ConfigTest.afterMethod",
                           "ConfigTest.beforeMethod (1)", "ConfigTest.afterMethod (1)",
                           "ConfigTest.afterClass"),
                   ContainerUtil.map(ContainerUtil.filter(root.getAllTests(), SMTestProxy::isConfig), SMTestProxy::getName));
    }
    finally {
      TestConsoleProperties.HIDE_PASSED_TESTS.set(properties, hidePassedTests);
      TestConsoleProperties.HIDE_SUCCESSFUL_CONFIG.set(properties, hideSuccessfulConfig);
    }
  }

  private static void collectVisibleTests(SMTRunnerTreeStructure treeStructure, SMTestProxy parent, List<String> names) {
    for (Object child : treeStructure.getChildElements(parent)) {
      SMTestProxy proxy = (SMTestProxy)child;
      if (proxy.isSuite()) {
        collectVisibleTests(treeStructure, proxy, names);
      }
      else {
        names.add(proxy.getName());
      }
    }
  }
}
