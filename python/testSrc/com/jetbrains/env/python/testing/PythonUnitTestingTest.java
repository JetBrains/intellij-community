// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.execution.testframework.sm.runner.GeneralIdBasedToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.testing.ConfigurationTarget;
import com.jetbrains.python.testing.PyUnitTestConfiguration;
import com.jetbrains.python.testing.PyUnitTestFactory;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.jetbrains.env.ut.PyScriptTestProcessRunner.TEST_TARGET_PREFIX;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PythonUnitTestingTest extends PythonUnitTestingLikeTest<PyUnitTestProcessRunner> {

  @Test(expected = RuntimeConfigurationWarning.class)
  public void testEmptyValidation() {
    new ConfigurationTarget("", PyRunTargetVariant.PATH).checkValid();
  }

  @Test(expected = RuntimeConfigurationError.class)
  public void testPythonValidation() {
    new ConfigurationTarget("c:/bad/", PyRunTargetVariant.PYTHON).checkValid();
  }

  @Test
  public void testValidationOk() {
    new ConfigurationTarget("foo.bar", PyRunTargetVariant.PYTHON).checkValid();
  }

  ///PY-31827
  @Test
  public void testNameChanged() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/nameChanged", "test_name_changed.py") {
      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        PyUnitTestProcessRunner runner = super.createProcessRunner();
        runner.setSkipExitCodeAssertion(true); //old runner is used
        return runner;
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      final int exitCode) {
        assertEquals("Test should not lead to errors, but:" + stderr, 0, exitCode);
      }
    });
  }

  /**
   * Run tests, delete file and click "rerun" should throw exception and display error since test ids do not point to correct PSI
   * from that moment
   */
  @Test
  public void testCantRerun() {
    runPythonTest(
      new PyUnitTestLikeProcessWithConsoleTestTask<>("/testRunner/env/unit", "test_with_skips_and_errors.py",
                                                     config -> {
                                                       // Second rerun should lead to exception
                                                       return createTestRunner(config.increaseRerunCount(2));
                                                     }) {
        @Override
        protected void exceptionThrown(@NotNull Throwable e, @NotNull PyUnitTestProcessRunner runner) {
          assertEquals("Wrong type of exception", e.getClass(), ExecutionException.class);
          assertTrue("Exception should be thrown on the first or second rerun", runner.getCurrentRerunStep() > 0);
          assertEquals(e.getMessage(), PyBundle.message("runcfg.tests.cant_rerun"));
        }

        @Override
        protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all, int exitCode) {
          assert runner.getFailedTestsCount() > 0 : "We need failed tests to test broken rerun";
          assertEquals("No exception thrown or rerun, but it should", 0, runner.getCurrentRerunStep());
          EdtTestUtil.runInEdtAndWait(() -> {
            // Delete files, so next rerun should lead to exception
            deleteAllTestFiles(myFixture);
          });
        }
      });
  }

  @Test
  public void testTcMessageEscaped() {
    final String[] messages = {
      "Hello\n",
      ServiceMessageBuilder.testStarted("myTest").toString(),
      ServiceMessageBuilder.testStdOut("myTest").addAttribute("out", "I am\n").toString(),
      ServiceMessageBuilder.testFinished("myTest").toString(),
      "PyCharm"
    };

    runPythonTest(new PyExecutionFixtureTestTask(null) {
      @Override
      public void runTestOn(@NotNull final String sdkHome, @Nullable Sdk existingSdk) throws Exception {
        final Project project = myFixture.getProject();
        final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.EMPTY_SDK);
        EdtTestUtil.runInEdtAndWait(() -> {
          final PythonConsoleView console = new PythonConsoleView(project, "test", sdk, true);

          console.getComponent(); //To init editor

          Arrays.stream(messages).forEach((s) -> console.print(s, ConsoleViewContentType.NORMAL_OUTPUT));
          console.flushDeferredText();
          try {
            assertEquals("TC messages filtered in wrong way", "Hello\nI am\nPyCharm", console.getText());
          }
          finally {
            Disposer.dispose(console);
          }
        });
      }
    });
  }

  @Test
  public void testSysPathOrder() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/sysPath", "test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner("", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyUnitTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.getTarget().setTargetType(PyRunTargetVariant.PYTHON);
            configuration.getTarget().setTarget("test.DemoTest");
            final VirtualFile subfolder = myFixture.getTempDirFixture().getFile("subfolder");
            assert subfolder != null;
            configuration.setWorkingDirectory(subfolder.getPath());
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        Assert.assertEquals("test failed: " + runner.getAllConsoleText(), 1, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testDiff() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/testDiff", "test_test.py") {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        Assert.assertThat("No expected", stdout, containsString("expected='1'"));
        Assert.assertThat("No actual", stdout, containsString("actual='2'"));
        var consoleError = MockPrinter.fillPrinter(runner.getTestProxy()).getStdErr();
        Assert.assertThat("No stacktrace", consoleError, containsString("test_test.py\", line 4,"));
        Assert.assertThat("Garbage in stack trace", consoleError, not(containsString("diff_tools.py")));
      }
    });
  }

  @Override
  PyUnitTestProcessRunner createTestRunner(@NotNull final TestRunnerConfig config) {
    return new PyUnitTestProcessRunner(config.getScriptName(), config.getRerunFailedTests());
  }

  @Test
  public void testSetupPyRunner() {
    // We need to make sure setup.py is called using different runner
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/failFast", "setup.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        PyUnitTestProcessRunner runner = super.createProcessRunner();
        runner.setSkipExitCodeAssertion(true); //setup.py doesn't support exit codes now
        return runner;
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertThat(all)
          .describedAs("Wrong runner used")
          .contains(PythonHelper.SETUPPY.asParamString());
      }
    });
  }

  @Test
  public void testRenameClass() {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameClassTask<>(
        PythonTestConfigurationType.getInstance().getUnitTestFactory().getName(),
        PyUnitTestConfiguration.class));
  }

  @Test(expected = RuntimeConfigurationWarning.class)
  public void testValidation() throws Throwable {

    runPythonTestWithException(
      new CreateConfigurationTestTask.PyConfigurationValidationTask<PyUnitTestConfiguration>() {
        @NotNull
        @Override
        protected PyUnitTestFactory createFactory() {
          return PythonTestConfigurationType.getInstance().getUnitTestFactory();
        }

        @Override
        protected void validateConfiguration() {
          final PyUnitTestConfiguration configuration = getConfiguration();
          configuration.setPattern("foo");
          configuration.getTarget().setTargetType(PyRunTargetVariant.PATH);
          configuration.getTarget().setTarget("foo.py");
          configuration.checkConfiguration();
        }
      });
  }

  /**
   * tests failfast as example of argument
   */
  @Test
  public void testFailFast() {

    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/failFast", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner(toFullPath(getMyScriptName()), 1) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyUnitTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setAdditionalArguments("-f"); //FailFast
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals("Runner did not stop after first fail", 1, runner.getAllTestsCount());
        assertEquals("Bad tree produced for failfast", """
          Test tree:
          [root](-)
          .test_test(-)
          ..SomeTestCase(-)
          ...test_1_test(-)
          """, runner.getFormattedTestTree());
      }
    });
  }

  /**
   * test should be launched from __init__.py
   */
  @Test
  public void testTestInInit() {

    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/testInInit", "") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner("", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyUnitTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.getTarget().setTargetType(PyRunTargetVariant.PYTHON);
            configuration.getTarget().setTarget("mymodule.ExampleModuleTestCase");
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals("wrong test launched", """
          Test tree:
          [root](+)
          .mymodule(+)
          ..ExampleModuleTestCase(+)
          ...test1(+)
          ...test2(+)
          """, runner.getFormattedTestTree());
        assertEquals("wrong exit code", 0, exitCode);
      }
    });
  }


  /**
   * check non-ascii (127+) chars are supported in skip messaged
   */
  @Test
  @EnvTestTagsRequired(tags = {}, skipOnFlavors = {})
  public void testNonAsciiMessage() {

    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/nonAscii", "test_test.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {

        runner.getFormattedTestTree();
        assertEquals("Skipped test with non-ascii message broke tree",
                     """
                       Test tree:
                       [root](~)
                       .test_test(~)
                       ..TestCase(~)
                       ...test(~)
                       """, runner.getFormattedTestTree());
        assertThat(stdout)
          .describedAs("non-ascii char broken in output")
          .contains("ошибка");
      }
    });
  }


  // Ensure failed and error subtests work
  @Test
  @EnvTestTagsRequired(tags = "python3")
  public void testSubTestError() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/subtestError", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner(toFullPath(getMyScriptName()), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        final String formattedTestTree = runner.getFormattedTestTree();
        assertEquals("Bad tree:" + formattedTestTree, """
          Test tree:
          [root](-)
          .test_test(-)
          ..TestThis(-)
          ...test_this(-)
          ....[test](-)
          """, formattedTestTree);
      }
    });
  }


  // Ensure failed and error subtests work
  @Test
  @EnvTestTagsRequired(tags = "python3")
  public void testSubTestAssertEqualsError() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/subtestError", "test_assert_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner(toFullPath(getMyScriptName()), 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        var printer = MockPrinter.fillPrinter(runner.findTestByName("[test]"));
        assertThat(printer.getStdErr())
          .describedAs("Subtest assertEquals broken")
          .contains("AssertionError: 'D' != 'a'");
      }
    });
  }

  /**
   * subtest may have names
   */
  @EnvTestTagsRequired(tags = "python3")
  @Test
  public void testWithNamedSubTests() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/withNamedSubtests", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner(toFullPath(getMyScriptName()), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals(3, runner.getFailedTestsCount());
        assertEquals(6, runner.getAllTestsCount());
        assertEquals("""
                       Test tree:
                       [root](-)
                       .test_test(-)
                       ..NumbersTest(-)
                       ...test_even (Test that numbers between 0 and 5 are all even_)(-)
                       ....(i=0)(+)
                       ....(i=1)(-)
                       ....(i=2)(+)
                       ....(i=3)(-)
                       ....(i=4)(+)
                       ....(i=5)(-)
                       """, runner.getFormattedTestTree());
      }
    });
  }


  /**
   * subtest names may have dots and shall not break test tree
   */
  @EnvTestTagsRequired(tags = "python3")
  @Test
  public void testDotsInSubtest() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/subtestDots", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner(toFullPath(getMyScriptName()), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals("dots in subtest names broke output", """
          Test tree:
          [root](-)
          .test_test(-)
          ..SampleTest(-)
          ...test_sample(-)
          ....(i='0_0')(-)
          ....(i='1_1')(-)
          ....(i='2_2')(+)
          ....(i='3_3')(+)
          ....(i='4_4')(+)
          ....(i='5_5')(+)
          ....(i='6_6')(+)
          ....(i='7_7')(+)
          ....(i='8_8')(+)
          ....(i='9_9')(+)
          """, runner.getFormattedTestTree());
      }
    });
  }


  /**
   * Raising SkipTest on class setup should not lead to KeyError
   */
  @Test
  public void testSkipInSetup() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/skipInSetup", "test_test.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals("Output tree broken for skipped exception thrown in setup method", """
                       Test tree:
                       [root](~)
                       .test_test(~)
                       ..TestSimple(~)
                       ...setUpClass(~)
                       ..TestSubSimple(~)
                       ...setUpClass(~)
                       """,
                     runner.getFormattedTestTree());
      }
    });
  }


  /**
   * Make sure test rerun works when pattern is enabled (PY-23416)
   */
  @Test
  @EnvTestTagsRequired(tags = "python3")
  public void testScriptWithHyphen() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/withHyphen", "test-foobar.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner(toFullPath(getMyScriptName()), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        if (runner.getCurrentRerunStep() == 0) {
          assertEquals(runner.getFormattedTestTree(), 2, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 1, runner.getPassedTestsCount());
          assertEquals(runner.getFormattedTestTree(), 1, runner.getFailedTestsCount());
        }
        else {
          assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 0, runner.getPassedTestsCount());
          assertEquals(runner.getFormattedTestTree(), 1, runner.getFailedTestsCount());
        }
      }
    });
  }


  /**
   * Make sure test rerun works when pattern is enabled (PY-23416)
   */
  @Test
  public void testPatternRerun() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/patternRerun", ".") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        // Full pass is required because it is folder
        return new PyUnitTestProcessRunner(toFullPath(getMyScriptName()), 2) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyUnitTestConfiguration configuration)
            throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setPattern("test*");
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        if (runner.getCurrentRerunStep() == 0) {
          assertEquals(stderr, 2, runner.getAllTestsCount());
        }
        else {
          assertEquals(stderr, 1, runner.getAllTestsCount());
        }
      }
    });
  }

  @Test
  public void testRerunSubfolder() {
    runPythonTest(new RerunSubfolderTask<PyUnitTestProcessRunner>(1) {
      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner(".", 1) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            // Unittest can't find tests in folders with out of init.py, even in py2k, so we set working dir explicitly
            configuration.setWorkingDirectory(toFullPath("tests"));
          }
        };
      }
    });
  }


  @EnvTestTagsRequired(tags = "python3") // Rerun for this scenario does not work for unitttest and py2
  //https://github.com/JetBrains/teamcity-messages/issues/129
  @Test
  public void testPackageInsideFolder() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/package_in_folder", "tests") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        // Full pass is required because it is folder
        return new PyUnitTestProcessRunner(toFullPath(getMyScriptName()), 2) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setWorkingDirectory(null); //Unset working dir: should be set to tests automatically
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull PyUnitTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all, int exitCode) {
        if (runner.getCurrentRerunStep() == 0) {
          assertEquals(runner.getFormattedTestTree(), 2, runner.getAllTestsCount());
        }
        else {
          assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
        }
      }
    });
  }

  @EnvTestTagsRequired(tags = "python3") // No subtest in py2
  @Test
  public void testSubtest() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/", "test_subtest.py", 1) {
      @Override
      protected void checkTestResults(@NotNull PyUnitTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all, int exitCode) {
        final String expectedResult = """
          Test tree:
          [root](-)
          .test_subtest(-)
          ..SpamTest(-)
          ...test_test(-)
          ....(i=0)(-)
          ....(i=1)(+)
          ....(i=2)(-)
          ....(i=3)(+)
          ....(i=4)(-)
          ....(i=5)(+)
          ....(i=6)(-)
          ....(i=7)(+)
          ....(i=8)(-)
          ....(i=9)(+)
          """;
        final String tree = runner.getFormattedTestTree();
        assertEquals("Bad tree:" + tree + ". \nStdout:" + stdout + "\n. Stderr: " + stderr, expectedResult, tree);
      }
    });
  }

  @EnvTestTagsRequired(tags = "python3") // No subtest in py2
  @Test
  public void testSubtestSkipped() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/", "test_skipped_subtest.py", 1) {
      @Override
      protected void checkTestResults(@NotNull PyUnitTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all, int exitCode) {
        assertEquals(runner.getFormattedTestTree(), 8, runner.getPassedTestsCount());
        assertEquals(runner.getFormattedTestTree(), 2, runner.getIgnoredTestsCount());
      }
    });
  }

  // PY-24407
  @Test
  public void testWorkingDirectoryDependsOnRelativeImport() {
    runPythonTest(new CreateConfigurationTestTask<>(PythonTestConfigurationType.getInstance().getUnitTestFactory().getName(),
                                                    PyUnitTestConfiguration.class) {
      @NotNull
      @Override
      protected List<PsiElement> getPsiElementsToRightClickOn() {
        myFixture.configureByFile("testRelativeImport/src/tests/test_no_relative.py");
        final PyFunction noRelativeImportFun = myFixture.findElementByText("test_no_relative", PyFunction.class);
        assert noRelativeImportFun != null;

        myFixture.configureByFile("testRelativeImport/src/tests/test_relative.py");
        final PyFunction relativeImportFun = myFixture.findElementByText("test_relative", PyFunction.class);
        assert relativeImportFun != null;


        return Arrays.asList(relativeImportFun, noRelativeImportFun);
      }

      @Override
      protected void checkConfiguration(@NotNull PyUnitTestConfiguration configuration, @NotNull PsiElement elementToRightClickOn) {
        super.checkConfiguration(configuration, elementToRightClickOn);
        configuration.getWorkingDirectorySafe();

        final PyFunction function = (PyFunction)elementToRightClickOn;
        if (function.getName().equals("test_relative")) {
          assertThat(configuration.getWorkingDirectory())
            .describedAs("Wrong dir  for relative import")
            .endsWith("testRelativeImport");
          assertEquals("Bad target", "src.tests.test_relative.ModuleTest.test_relative", configuration.getTarget().getTarget());
        }
        else if (function.getName().equals("test_no_relative")) {
          assertThat(configuration.getWorkingDirectory())
            .describedAs("Wrong dir for non relative import")
            .endsWith("testRelativeImport/src/tests");
          assertEquals("Bad target", "test_no_relative.ModuleTest.test_no_relative", configuration.getTarget().getTarget());
        }
        else {
          throw new AssertionError("Unexpected function " + function.getName());
        }
      }
    });
  }

  /**
   * Checks tests are resolved when launched from subfolder
   */
  @Test
  public void testTestsInSubFolderResolvable() {
    runPythonTest(
      new PyTestsInSubFolderRunner<PyUnitTestProcessRunner>("test_metheggs", "test_first") {
        @NotNull
        @Override
        protected PyUnitTestProcessRunner createProcessRunner() {
          return new PyUnitTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      });
  }

  /**
   * Ensures test output works
   */
  @Test
  public void testOutput() {
    runPythonTest(
      new PyTestsOutputRunner<PyUnitTestProcessRunner>("test_metheggs", "test_first") {
        @NotNull
        @Override
        protected PyUnitTestProcessRunner createProcessRunner() {
          return new PyUnitTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      });
  }


  /**
   * Checks <a href="https://docs.python.org/2/library/unittest.html#load-tests-protocol">Load test protocol</a>
   */
  @Test
  public void testLoadProtocol() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test_load_protocol.py") {
      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        // "load_protocol" does not exist before 2.7
        return level.compareTo(LanguageLevel.PYTHON26) > 0;
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals("bad num of passed tests: unittest load protocol failed to find tests?", 3, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();
      }
    });
  }

  /**
   * Ensures pattern is supported
   */
  @Test
  public void testUTRunnerByPattern() {
    runPythonTest(
      new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", PyUnitTestProcessRunner.TEST_PATTERN_PREFIX + "*pattern.py") {


        @Override
        protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all, int exitCode) {
          assertEquals(runner.getFormattedTestTree(), 4, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 2, runner.getPassedTestsCount());
          assertEquals(runner.getFormattedTestTree(), 2, runner.getFailedTestsCount());
        }
      });
  }

  @Test
  public void testMethod() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit",
                                                           TEST_TARGET_PREFIX +
                                                           "test_file.GoodTest.test_passes") {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testRelativeImports() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit/relativeImports",
                                                           PyUnitTestProcessRunner.TEST_PATTERN_PREFIX + "test_imps.py") {
      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
        assertEquals(runner.getFormattedTestTree(), 1, runner.getPassedTestsCount());
      }
    });
  }


  @Test
  public void testConfigurationProducerOnDirectory() {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameFolderTask<>(
        PythonTestConfigurationType.getInstance().getUnitTestFactory().getName(),
        PyUnitTestConfiguration.class));
  }


  @Test
  public void testConfigurationProducer() {
    runPythonTest(
      new CreateConfigurationByFileTask<>(new PyUnitTestFactory(PythonTestConfigurationType.getInstance()).getName(), PyUnitTestConfiguration.class));
  }

  /**
   * Ensures newly created configuration inherits working dir from default if set
   */
  @Test
  public void testConfigurationProducerObeysDefaultDir() {
    runPythonTest(
      new CreateConfigurationByFileTask<>(new PyUnitTestFactory(PythonTestConfigurationType.getInstance()).getName(),
                                          PyUnitTestConfiguration.class) {
        private static final String SOME_RANDOM_DIR = "//some/random/ddir";

        @Override
        public void runTestOn(@NotNull final String sdkHome, @Nullable Sdk existingSdk) throws InvalidSdkException {
          // Set default working directory to some random location before actual exection
          final PyUnitTestConfiguration templateConfiguration = getTemplateConfiguration(new PyUnitTestFactory(PythonTestConfigurationType.getInstance()));
          templateConfiguration.setWorkingDirectory(SOME_RANDOM_DIR);
          super.runTestOn(sdkHome, existingSdk);
          templateConfiguration.setWorkingDirectory("");
        }

        @Override
        protected void checkConfiguration(@NotNull final PyUnitTestConfiguration configuration,
                                          @NotNull final PsiElement elementToRightClickOn) {
          super.checkConfiguration(configuration, elementToRightClickOn);
          assertEquals("UnitTest does not obey default working directory", SOME_RANDOM_DIR,
                       configuration.getWorkingDirectorySafe());
        }
      }
    );
  }


  private abstract class PyUnitTestProcessWithConsoleTestTask extends PyUnitTestLikeProcessWithConsoleTestTask<PyUnitTestProcessRunner> {
    PyUnitTestProcessWithConsoleTestTask(@NotNull String relativePathToTestData,
                                         @NotNull String scriptName) {
      super(relativePathToTestData, scriptName, PythonUnitTestingTest.this::createTestRunner);
    }

    PyUnitTestProcessWithConsoleTestTask(@NotNull String relativePathToTestData,
                                         @NotNull String scriptName,
                                         int rerunFailedTests) {
      super(relativePathToTestData, scriptName, rerunFailedTests, PythonUnitTestingTest.this::createTestRunner);
    }

    @Override
    public @NotNull Collection<Class<?>> getClassesToEnableDebug() {
      return List.of(GeneralIdBasedToSMTRunnerEventsConvertor.class);
    }
  }

  /**
   * Deletes all files in temp. folder
   */
  private static void deleteAllTestFiles(@NotNull final CodeInsightTestFixture fixture) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final VirtualFile testRoot = fixture.getTempDirFixture().getFile(".");
      assert testRoot != null : "No temp path?";
      try {
        for (final VirtualFile child : testRoot.getChildren()) {
          child.delete(null);
        }
      }
      catch (final IOException e) {
        throw new AssertionError(String.format("Failed to delete files in  %s : %s", testRoot, e));
      }
    });
  }
}
