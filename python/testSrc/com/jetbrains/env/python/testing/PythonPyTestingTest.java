package com.jetbrains.env.python.testing;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.python.testing.CreateConfigurationTestTask.PyConfigurationValidationTask;
import com.jetbrains.env.ut.PyTestTestProcessRunner;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.testing.*;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import static com.intellij.execution.testframework.sm.runner.ui.MockPrinter.fillPrinter;
import static com.jetbrains.env.ut.PyScriptTestProcessRunner.TEST_TARGET_PREFIX;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * User : catherine
 */
@EnvTestTagsRequired(tags = "pytest")
public final class PythonPyTestingTest extends PyEnvTestCase {


  // Ensures setup/teardown does not break anything
  @Test
  public void testSetupTearDown() {
    runPythonTest(new SetupTearDownTestTask<PyTestTestProcessRunner>() {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_test.py", 1);
      }
    });
  }

  /**
   * assertEquals from TestCase launched by pytest
   */
  @Test
  public void testDiffUnit() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/diff_unit", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_diff.py", 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {

        Iterable<Matcher<? super String>> expectedConsoleText =
          ContainerUtil.map(ImmutableList.of("Expected :'A'", "Actual   :'B'", "<Click to see difference>"), Matchers::containsString);
        var consoleText = runner.getAllConsoleText();
        assertThat("No diff", consoleText, allOf(expectedConsoleText));
        assertThat("Wrong line", consoleText, containsString("test_diff.py:7: AssertionError"));
      }
    });
  }

  @Test
  public void testDiff() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/diff", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_diff.py", 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {

        final String expectedConsoleText = "Expected :'expected'\n" + "Actual   :'actual'\n" + "<Click to see difference>";
        assertThat("No diff", runner.getAllConsoleText(), containsString(expectedConsoleText));
      }
    });
  }

  /**
   * Provides existing .xml and checks that configuration is able to parse it
   */
  @Test
  public void testDeserialization() {
    runPythonTest(new PyExecutionFixtureTestTask("testRunner/env/pytest/config/") {
      @Override
      public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
        final PyTestConfiguration configuration = configureByFile("MyConfig.xml");
        assertEquals("Wrong target type", new ConfigurationTarget("foo.py", PyRunTargetVariant.PATH), configuration.getTarget());
        assertEquals("Wrong keywords", "keywords", configuration.getKeywords());
        assertEquals("Wrong arguments", "--additional-args", configuration.getAdditionalArguments());


        final PyTestConfiguration configurationWithCustom = configureByFile("MyConfigWithCustom.xml");
        assertEquals("Wrong target type", new ConfigurationTarget("spam", PyRunTargetVariant.CUSTOM), configurationWithCustom.getTarget());
        assertEquals("Wrong keywords", "keywords", configurationWithCustom.getKeywords());
        assertEquals("Wrong arguments", "--additional-args", configurationWithCustom.getAdditionalArguments());
      }

      @NotNull
      private PyTestConfiguration configureByFile(@NotNull final String path) throws IOException, JDOMException {
        final VirtualFile file = myFixture.getTempDirFixture().getFile(path);
        assert file != null : "No config found";

        final SAXBuilder builder = new SAXBuilder();
        final CharBuffer data = Charset.defaultCharset().decode(ByteBuffer.wrap(file.contentsToByteArray()));
        final Element element = builder.build(new StringReader(data.toString())).getRootElement();

        final PyTestConfiguration configuration =
          new PyTestConfiguration(myFixture.getProject(), new PyTestFactory(PythonTestConfigurationType.getInstance()));
        configuration.readExternal(element);
        return configuration;
      }
    });
  }

  /**
   * Ensures that python target pointing to module works correctly
   */
  @Test
  public void testRunModuleAsFile() {
    runPythonTest(new RunModuleAsFileTask<PyTestTestProcessRunner>() {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner(TARGET, 0);
      }
    });
  }


  @Test
  public void testRerunSubfolder() {
    runPythonTest(new RerunSubfolderTask<PyTestTestProcessRunner>(2) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner(".", 1);
      }
    });
  }

  @EnvTestTagsRequired(tags = "-messages") //messages registered 2 times when launched with testdir plugin, should be fixed separately
  @Test
  public void testTestDirFixture() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/testdir", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_foo.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {

        assertEquals(stderr, 1, runner.getAllTestsCount());
        assertEquals(stderr, 1, runner.getPassedTestsCount());
      }
    });
  }

  /**
   * Test name must be reported as meta info to be used as parameter for for parametrized tests
   */
  @Test
  public void testMetaInfoForMethod() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_with_method.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {

        final String testName = "test_method";
        final AbstractTestProxy method = runner.findTestByName(testName);
        assert method != null : "Method not reported";
        assertEquals("Meta info must be test name", testName, method.getMetainfo());
      }
    });
  }


  @Test
  public void testParametrized() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/parametrized", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner("test_pytest_parametrized.py", 1);
        }

        @Override
        protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all,
                                        int exitCode) {
          assertEquals("Parametrized test produced bad tree", "Test tree:\n" +
                                                              "[root](-)\n" +
                                                              ".test_pytest_parametrized(-)\n" +
                                                              "..test_eval(-)\n" +
                                                              "...(three plus file-8)(-)\n" +
                                                              ((runner.getCurrentRerunStep() == 0) ? "...((2)+(4)-6)(+)\n" : "") +
                                                              "...( six times nine_-42)(-)\n", runner.getFormattedTestTree());
        }
      });
  }

  /**
   * Ensure that testName[param] is only launched for parametrized test if param provided
   */
  @Test
  public void testParametrizedRunByParameter() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/parametrized", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner("test_pytest_parametrized.py", 1) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.getTarget().setTarget("test_pytest_parametrized.test_eval");
              configuration.getTarget().setTargetType(PyRunTargetVariant.PYTHON);
              configuration.setAdditionalArguments("--debug");
              configuration.setMetaInfo("test_eval[three plus file-8]");
            }
          };
        }

        @Override
        protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all,
                                        int exitCode) {
          assertEquals("Only one test should be launched", "Test tree:\n" +
                                                           "[root](-)\n" +
                                                           ".test_pytest_parametrized(-)\n" +
                                                           "..test_eval(-)\n" +
                                                           "...(three plus file-8)(-)\n", runner.getFormattedTestTree());
        }
      });
  }


  /**
   * See https://github.com/JetBrains/teamcity-messages/issues/131
   */
  @Test
  public void testTestNameBeforeTestStarted() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/testNameBeforeTestStarted",
                                                                            SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_test.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        assertEquals("Test name before message broke output", "Test tree:\n" +
                                                              "[root](+)\n" +
                                                              ".test_test(+)\n" +
                                                              "..SampleTest1(+)\n" +
                                                              "...test_sample_1(+)\n" +
                                                              "...test_sample_2(+)\n" +
                                                              "...test_sample_3(+)\n" +
                                                              "...test_sample_4(+)\n" +
                                                              "..SampleTest2(+)\n" +
                                                              "...test_sample_5(+)\n" +
                                                              "...test_sample_6(+)\n" +
                                                              "...test_sample_7(+)\n" +
                                                              "...test_sample_8(+)\n", runner.getFormattedTestTree());
      }
    });
  }


  /**
   * Each node must contain "raise Exception" only once and no "Assertion Failed" message
   */
  @EnvTestTagsRequired(tags = "python3")
  @Test
  public void testAssertionFailedNotDuplicated() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/fail_tree/", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner(TEST_TARGET_PREFIX + "test_test", 0);
        }

        @Override
        protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all,
                                        final int exitCode) {
          var assertionFailedMessage = TestStateInfo.Magnitude.FAILED_INDEX.getTitle();
          var errorMessage = "raise Exception";

          var tests = new AbstractTestProxy[]{runner.getTestProxy(), runner.findTestByName("TestTest"), runner.findTestByName("test_test")};
          for (var testText : ContainerUtil.map(tests, o -> fillPrinter(o).getAllOut())) {
            assertThat("Redundant error message", testText, not(containsString(assertionFailedMessage)));
            assertThat("No required error message", testText, containsString(errorMessage));
            assertEquals("Wrong number of real error messages", 2, testText.split(errorMessage).length);
          }
        }
      });
  }

  @Test
  public void testKeywords() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/keywords", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_test.py", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setKeywords("not spam");
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      final int exitCode) {
        assertEquals("Test tree:\n" + "[root](+)\n" + ".test_test(+)\n" + "..test_eggs(+)\n", runner.getFormattedTestTree());
        assertEquals(0, exitCode);
      }
    });
  }

  @Test
  public void testKeywordsIgnoredInCustom() {
    runTestWithJunkKeywords(true);
  }

  @Test
  public void testTestEmptySuite() {
    runTestWithJunkKeywords(false);
  }

  private void runTestWithJunkKeywords(boolean useCustomMode) {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/testNameBeforeTestStarted",
                                                                            SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_test.py", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setKeywords("asdasdasd");
            if (useCustomMode) {
              configuration.getTarget().setTargetType(PyRunTargetVariant.CUSTOM);
            }
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      final int exitCode) {
        if (useCustomMode) {
          assertEquals("Wrong test launched", "Test tree:\n" +
                                              "[root](+)\n" +
                                              ".test_test(+)\n" +
                                              "..SampleTest1(+)\n" +
                                              "...test_sample_1(+)\n" +
                                              "...test_sample_2(+)\n" +
                                              "...test_sample_3(+)\n" +
                                              "...test_sample_4(+)\n" +
                                              "..SampleTest2(+)\n" +
                                              "...test_sample_5(+)\n" +
                                              "...test_sample_6(+)\n" +
                                              "...test_sample_7(+)\n" +
                                              "...test_sample_8(+)\n", runner.getFormattedTestTree());
          runner.getFormattedTestTree();
        }
        else {
          assertEquals("Wrong message for empty suite", PyBundle.message("runcfg.tests.empty_suite"),
                       runner.getTestProxy().getPresentation());
          assertEquals("Wrong empty suite tree", "Test tree:\n" + "[root](-)\n", runner.getFormattedTestTree());
        }
      }
    });
  }


  @Test
  @EnvTestTagsRequired(tags = "xdist")
  public void testParallelWithSetup() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/parallel", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_parallel.py", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setAdditionalArguments("-n 4");
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull PyTestTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all,
                                      int exitCode) {
        assertThat("xdist not launched?", all, containsString("gw0"));
        assertEquals("Test tree:\n" + "[root](+)\n" + ".test_parallel(+)\n" + "..ExampleTestCase(+)\n" + "...test_example(+)\n",
                     runner.getFormattedTestTree());
      }
    });
  }

  // Ensure test survives patched strftime
  @Test
  public void testMonkeyPatch() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/monkeyPatch", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner("test_test.py", 0);
        }

        @Override
        protected void checkTestResults(@NotNull PyTestTestProcessRunner runner,
                                        @NotNull String stdout,
                                        @NotNull String stderr,
                                        @NotNull String all,
                                        int exitCode) {
          assertEquals("Monkeypatch broke the test: " + stderr, 1, runner.getPassedTestsCount());
        }
      });
  }

  // Ensure slow test is not run when -m "not slow" is provided
  @Test
  public void testMarkerWithSpaces() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/test_with_markers", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner("test_with_markers.py", 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setAdditionalArguments("-m 'not slow'");
            }
          };
        }


        @Override
        protected void checkTestResults(@NotNull PyTestTestProcessRunner runner,
                                        @NotNull String stdout,
                                        @NotNull String stderr,
                                        @NotNull String all,
                                        int exitCode) {
          assertEquals("Marker support broken", "Test tree:\n" + "[root](+)\n" + ".test_with_markers(+)\n" + "..test_fast(+)\n",
                       runner.getFormattedTestTree());
        }
      });
  }


  /**
   * New configuration should have closest src set as its working dir
   */
  @Test
  public void testClosestSrcIsWorkDirOnNewConfig() {
    runPythonTest(new CreateConfigurationTestTask<>(getFrameworkId(), PyTestConfiguration.class) {
      @NotNull
      @Override
      protected List<PsiElement> getPsiElementsToRightClickOn() {
        configureSrcFolder(myFixture);

        myFixture.configureByFile("test_with_src/foo/src/test_test.py");
        final PyFunction test = myFixture.findElementByText("test_test", PyFunction.class);
        assert test != null;
        return Collections.singletonList(test);
      }

      @Override
      protected void checkConfiguration(@NotNull PyTestConfiguration configuration, @NotNull PsiElement elementToRightClickOn) {
        super.checkConfiguration(configuration, elementToRightClickOn);
        assertThat("Wrong configuration directory set on new config", configuration.getWorkingDirectory(), Matchers.endsWith("src"));
      }
    });
  }

  /**
   * In case when workdir is not set we should use closest src
   */
  @Test
  public void testClosestSrcIsWorkDirDynamically() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/createConfigurationTest/", SdkCreationType.EMPTY_SDK) {
        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner(TEST_TARGET_PREFIX + "test_test.test_test", 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              // Reset dir to check it is calculated correctly
              configuration.setWorkingDirectory(null);
              configureSrcFolder(myFixture);
              assertThat("Wrong configuration directory calculated", configuration.getWorkingDirectorySafe(), Matchers.endsWith("src"));
            }
          };
        }


        @Override
        protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all,
                                        int exitCode) {
          assertEquals("Failed to run test" + stderr, 1, runner.getPassedTestsCount());
        }
      });
  }

  //TODO: DOC
  private static void configureSrcFolder(@NotNull final CodeInsightTestFixture fixture) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(fixture.getModule());
    final ModifiableRootModel model = manager.getModifiableModel();
    final VirtualFile srcToMark = fixture.getTempDirFixture().getFile("test_with_src/foo/src");
    assert srcToMark != null;
    model.addContentEntry(srcToMark);
    model.commit();
  }

  @Test
  public void testConfigurationProducer() {
    runPythonTest(new CreateConfigurationByFileTask<>(getFrameworkId(), PyTestConfiguration.class));
  }

  @Test
  @EnvTestTagsRequired(tags = "python3")
  public void testResolveQName() {
    runPythonTest(new CreateConfigurationTestTask.PyConfigurationCreationTask() {
      @NotNull
      @Override
      protected PyAbstractTestFactory<PyTestConfiguration> createFactory() {
        return new PyTestFactory(PythonTestConfigurationType.getInstance());
      }

      @Override
      public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) {
        super.runTestOn(sdkHome, existingSdk);
        myFixture.copyDirectoryToProject("testRunner/env/createConfigurationTest/configurationByContext/foo", "");
        final PyAbstractTestConfiguration configuration = getConfiguration();
        configuration.getTarget().setTargetType(PyRunTargetVariant.PYTHON);
        configuration.getTarget().setTarget("test_foo");
        configuration.setWorkingDirectory(myFixture.getTempDirPath());

        ReadAction.run(() -> assertThat("Failed to resolve qname", configuration.getTarget().asPsiElement(configuration),
                                        Matchers.instanceOf(PyFile.class)));
      }
    });
  }

  @Test
  public void testMultipleCases() {
    runPythonTest(new CreateConfigurationMultipleCasesTask<>(getFrameworkId(), PyTestConfiguration.class));
  }

  @Test
  public void testFileStartsWithDirName() {
    runPythonTest(new CreateConfigurationTestTask<>(getFrameworkId(), PyTestConfiguration.class) {
      private static final String FOLDER_NAME = "test";

      @Override
      public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws InvalidSdkException {
        markFolderAsTestRoot(FOLDER_NAME);
        super.runTestOn(sdkHome, existingSdk);
      }

      @Override
      protected void checkConfiguration(@NotNull final PyTestConfiguration configuration, @NotNull final PsiElement elementToRightClickOn) {
        assertEquals("Wrong target created", "test_test.TestFoo", configuration.getTarget().getTarget());
      }

      @NotNull
      @Override
      protected List<PsiElement> getPsiElementsToRightClickOn() {
        var file = (PyFile)myFixture.configureByFile(FOLDER_NAME + "/test_test.py");
        var clazz = file.findTopLevelClass("TestFoo");
        assert clazz != null : "No test found";
        return Collections.singletonList(clazz);
      }
    });
  }

  /**
   * PY-49932
   */
  @Test
  public void testFilesSameName() {
    runPythonTest(new CreateConfigurationTestTask<>(getFrameworkId(), PyTestConfiguration.class) {
      private static final String FOLDER_NAME = "same_names";

      @Override
      public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws InvalidSdkException {
        markFolderAsTestRoot(FOLDER_NAME);
        super.runTestOn(sdkHome, existingSdk);
      }

      @Override
      protected void checkConfiguration(@NotNull final PyTestConfiguration configuration, @NotNull final PsiElement elementToRightClickOn) {
        assertEquals("Wrong target fore newly created element", "true.test_something.test_test", configuration.getTarget().getTarget());
      }

      @NotNull
      @Override
      protected List<PsiElement> getPsiElementsToRightClickOn() {
        var file = (PyFile)myFixture.configureByFile(FOLDER_NAME + "/true/test_something.py");
        PyFunction test = file.findTopLevelFunction("test_test");
        assert test != null : "No test_test found";
        return Collections.singletonList(test);
      }
    });
  }

  /**
   * Create configuration by right click and check that same configuration is chosen when clicked on same element.
   * New one created in other case.
   */
  @Test
  public void testConfigurationByContext() {
    runPythonTest(new CreateConfigurationTestTask<>(getFrameworkId(), PyTestConfiguration.class) {


      @NotNull
      private PyFunction getFunction(@NotNull final String folder) {
        final PyFile file = (PyFile)myFixture.configureByFile(String.format("configurationByContext/%s/test_foo.py", folder));
        assert file != null;
        final PyFunction function = file.findTopLevelFunction("test_test");
        assert function != null;
        return function;
      }

      @Override
      protected void checkConfiguration(@NotNull final PyTestConfiguration configuration, @NotNull final PsiElement elementToRightClickOn) {


        PyFunction bar = getFunction("bar");
        final PyTestConfiguration sameConfig = createConfigurationByElement(bar, PyTestConfiguration.class);
        assertEquals("Same element must provide same config", sameConfig, configuration);

        PyFunction foo = getFunction("foo");
        final PyTestConfiguration differentConfig = createConfigurationByElement(foo, PyTestConfiguration.class);
        //Although targets are same, working dirs are different
        assert differentConfig.getTarget().equals(configuration.getTarget());

        assertNotEquals("Function from different folder must provide different config", differentConfig, configuration);

        try {
          // Test "custom symbol" mode: instead of QN we must get custom with additional arguments pointing to file and symbol
          ((PyTestConfiguration)RunManager.getInstance(getProject())
            .getConfigurationTemplate(new PyTestFactory(PythonTestConfigurationType.getInstance())).getConfiguration()).setWorkingDirectory(
            bar.getContainingFile().getParent().getVirtualFile().getPath());
          PyTestConfiguration customConfiguration = createConfigurationByElement(foo, PyTestConfiguration.class);
          assertEquals(PyRunTargetVariant.CUSTOM, customConfiguration.getTarget().getTargetType());
          assertEquals(foo.getContainingFile().getVirtualFile().getPath() + "::test_test", customConfiguration.getAdditionalArguments());
        }
        finally {
          ((PyTestConfiguration)RunManager.getInstance(getProject())
            .getConfigurationTemplate(new PyTestFactory(PythonTestConfigurationType.getInstance())).getConfiguration()).setWorkingDirectory(
            null);
        }
      }

      @NotNull
      @Override
      protected List<PsiElement> getPsiElementsToRightClickOn() {
        return Collections.singletonList(getFunction("bar"));
      }
    });
  }

  /**
   * Checks tests are resolved when launched from subfolder
   */
  @Test
  public void testTestsInSubFolderResolvable() {
    runPythonTest(new PyTestsInSubFolderRunner<PyTestTestProcessRunner>("test_metheggs", "test_funeggs", "test_first") {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner(toFullPath("tests"), 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setWorkingDirectory(getWorkingFolderForScript());
          }
        };
      }
    });
  }

  /**
   * Set test runner to autodetect, and ensure pytest fixture still works
   */
  @Test
  public void testFixturePyTestAutoDetected() {
    runPythonTest(new PyExecutionFixtureTestTask(PyTestFixtureAndParametrizedTest.testSubfolder) {
      @Override
      public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws InvalidSdkException, ExecutionException {
        Sdk sdk = createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_ONLY);
        PyPackageManager.getInstance(sdk).refreshAndGetPackages(true);
        var runnerService = TestRunnerService.getInstance(myFixture.getModule());
        runnerService.setSelectedFactory(PythonTestConfigurationType.getInstance().getAutoDetectFactory());
        ApplicationManager.getApplication().invokeAndWait(() -> PyTestFixtureAndParametrizedTest.Companion.testInspectionStatic(myFixture));
      }
    });
  }

  /**
   * Ensures test output works
   */
  @Test
  public void testOutput() {
    runPythonTest(new PyTestsOutputRunner<PyTestTestProcessRunner>("test_metheggs", "test_funeggs", "test_first") {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner(toFullPath("tests"), 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setWorkingDirectory(getWorkingFolderForScript());
          }
        };
      }
    });
  }

  @Test(expected = RuntimeConfigurationWarning.class)
  public void testValidation() throws Throwable {
    runPythonTestWithException(new PyConfigurationValidationTask<PyTestConfiguration>() {
      @NotNull
      @Override
      protected PyTestFactory createFactory() {
        return new PyTestFactory(PythonTestConfigurationType.getInstance());
      }
    });
  }

  @Test
  public void testConfigurationProducerOnDirectory() {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameFolderTask<>(getFrameworkId(), PyTestConfiguration.class));
  }

  @Test
  public void testProduceConfigurationOnFile() {
    runPythonTest(new CreateConfigurationByFileTask<>(getFrameworkId(), PyTestConfiguration.class, "spam.py") {
      @NotNull
      @Override
      protected PsiElement getElementToRightClickOnByFile(@NotNull final String fileName) {
        return myFixture.configureByFile(fileName);
      }
    });
  }

  @Test
  public void testRenameClass() {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameClassTask<>(getFrameworkId(), PyTestConfiguration.class));
  }

  /**
   * Ensure dots in test names do not break anything (PY-13833)
   */
  @Test
  public void testEscape() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_escape_me.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          final String resultTree = runner.getFormattedTestTree().trim();
          final String expectedTree = myFixture.configureByFile("test_escape_me.tree.txt").getText().trim();
          assertEquals("Test result wrong tree", expectedTree, resultTree);
        });
      }
    });
  }

  // Import error should lead to test failure
  @Test
  public void testFailInCaseOfError() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/failTest", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner(".", 0);
      }


      @Override
      protected void checkTestResults(@NotNull PyTestTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all,
                                      int exitCode) {
        assertThat("Import error is not marked as error", runner.getFailedTestsCount(), Matchers.greaterThanOrEqualTo(1));
      }
    });
  }

  /**
   * Ensure element dir is used as curdir even if not set explicitly
   */
  @Test
  public void testCurrentDir() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setWorkingDirectory(null);
            final VirtualFile fullFilePath = myFixture.getTempDirFixture().getFile("dir_test.py");
            assert fullFilePath != null : String.format("No dir_test.py in %s", myFixture.getTempDirFixture().getTempDirPath());
            configuration.getTarget().setTarget(fullFilePath.getPath());
            configuration.getTarget().setTargetType(PyRunTargetVariant.PATH);
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        final String projectDir = myFixture.getTempDirFixture().getTempDirPath();
        assertThat("No directory found in output", runner.getConsole().getText(),
                   containsString(String.format("Directory %s", PathUtil.toSystemDependentName(projectDir))));
      }
    });
  }

  @Test
  public void testPytestRunner() {

    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test1.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        assertEquals(3, runner.getAllTestsCount());
        assertEquals(3, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();


        // This test has "sleep(1)", so duration should be >=1000
        final AbstractTestProxy testForOneSecond = runner.findTestByName("testOne");
        assertThat("Wrong duration", testForOneSecond.getDuration(), Matchers.greaterThanOrEqualTo(1000L));
      }
    });
  }

  /**
   * Ensure we can run path like "spam.bar" where "spam" is folder with out of init.py
   */
  @Test
  public void testPyTestFolderNoInitPy() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        if (getLevelForSdk().isPy3K()) {
          return new PyTestTestProcessRunner("folder_no_init_py/test_test.py", 2);
        }
        else {
          return new PyTestTestProcessRunner(toFullPath("folder_no_init_py/test_test.py"), 2) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        assertEquals(runner.getFormattedTestTree(), 1, runner.getFailedTestsCount());
        if (runner.getCurrentRerunStep() == 0) {
          assertEquals(runner.getFormattedTestTree(), 2, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 1, runner.getPassedTestsCount());
        }
        else {
          assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 0, runner.getPassedTestsCount());
        }
      }
    });
  }

  //PY-32431
  @Test
  public void testRerunWithParent() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/rerun", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test:test_subsystems.TestBar", 2);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        assertEquals("Wrong number of passed tests", 0, runner.getPassedTestsCount());
        assertEquals("Wrong number of failed tests", 1, runner.getFailedTestsCount());
        assertEquals("Wrong tests executed",
                     "Test tree:\n" + "[root](-)\n" + ".test_subsystems(-)\n" + "..TestBar(-)\n" + "...test_something(-)\n",
                     runner.getFormattedTestTree());
      }
    });
  }

  /**
   * Rerun failed with with additional arguments
   */
  @Test
  public void testRerunWithArguments() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/rerun", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("failed_test.py", 2) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setAdditionalArguments("--log-level=INFO -vv");
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        assert runner.getFailedTestsCount() == 1 : "Test must fail";
        assertThat("No -vv argument in command line", stdout, containsString("-vv"));
      }
    });
  }


  @Test
  public void testPytestRunner2() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test2.py", 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        if (runner.getCurrentRerunStep() > 0) {
          assertEquals(runner.getFormattedTestTree(), 4, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 0, runner.getPassedTestsCount());
          assertEquals(runner.getFormattedTestTree(), 4, runner.getFailedTestsCount());
          return;
        }
        assertEquals(runner.getFormattedTestTree(), 9, runner.getAllTestsCount());
        assertEquals(runner.getFormattedTestTree(), 5, runner.getPassedTestsCount());
        assertEquals(runner.getFormattedTestTree(), 4, runner.getFailedTestsCount());
        // Py.test may report F before failed test, so we check string contains, not starts with
        assertThat("No test stdout", fillPrinter(runner.findTestByName("testOne")).getStdOut(), containsString("I am test1"));

        // Ensure test has stdout even it fails
        final AbstractTestProxy testFail = runner.findTestByName("testFail");
        assertThat("No stdout for fail", fillPrinter(testFail).getStdOut(), containsString("I will fail"));

        // This test has "sleep(1)", so duration should be >=1000
        assertThat("Wrong duration", testFail.getDuration(), Matchers.greaterThanOrEqualTo(900L));
      }
    });
  }


  /**
   * Ensures file references are highlighted for pytest traceback
   */
  @Test
  public void testPyTestFileReferences() {
    final String fileName = "reference_tests.py";

    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/unit", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner(fileName, 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all,
                                      int exitCode) {
        final List<String> fileNames = runner.getHighlightedStringsInConsole().second;
        assertThat("No lines highlighted", fileNames, not(Matchers.empty()));
        // PyTest highlights file:line_number
        assertTrue("Assert fail not marked", fileNames.contains("reference_tests.py:7"));
        assertTrue("Failed test not marked", fileNames.contains("reference_tests.py:12"));
        assertTrue("Failed test not marked", fileNames.contains("reference_tests.py"));
      }
    });
  }

  @NotNull
  private static String getFrameworkId() {
    return PyTestFactory.id;
  }
}
