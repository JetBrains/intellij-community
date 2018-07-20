// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env;

import com.google.common.collect.Sets;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.testing.tox.PyToxConfiguration;
import com.jetbrains.python.testing.tox.PyToxConfigurationFactory;
import com.jetbrains.python.testing.tox.PyToxTestTools;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.assertj.core.api.Condition;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensure tox runner works
 *
 * @author Ilya.Kazakevich
 */
public final class PyToxTest extends PyEnvTestCase {
  public PyToxTest() {
    super("tox");
  }

  /**
   * Simply ensure tox runner works
   */
  @Test
  public void testToxSimpleRun() {
    runPythonTest(new MyPyProcessWithConsoleTestTask("/toxtest/toxSimpleRun/", 2,
                                                     () -> new MyTestProcessRunner(),
                                                     Collections.singletonList(
                                                       Pair.create("py27", new InterpreterExpectations("", true))
                                                     ),
                                                     Integer.MAX_VALUE));
  }

  /**
   * Check tox nose runner
   */
  @Test
  public void testToxNose() {
    runPythonTest(new MyPyProcessWithConsoleTestTask("/toxtest/toxNose/", 1,
                                                     () -> new MyTestProcessRunner(),
                                                     Arrays.asList(
                                                       Pair.create("py27", new InterpreterExpectations("", true)),
                                                       // Does not support 3.4
                                                       Pair.create("py34", new InterpreterExpectations("SyntaxError", false))
                                                     ),
                                                     Integer.MAX_VALUE)
    );
  }

  /**
   * Check tox pytest runner
   */
  @Test
  public void testToxPyTest() {
    runPythonTest(new MyPyProcessWithConsoleTestTask("/toxtest/toxPyTest/", 1,
                                                     () -> new MyTestProcessRunner(),
                                                     Arrays.asList(
                                                       Pair.create("py27", new InterpreterExpectations("", true)),
                                                       // Does not support 3.4
                                                       Pair.create("py34", new InterpreterExpectations("SyntaxError", false))
                                                     ),
                                                     Integer.MAX_VALUE)
    );
  }

  /**
   * Check tox unit runner
   */
  @Test
  public void testToxUnitTest() {
    runPythonTest(new MyPyProcessWithConsoleTestTask("/toxtest/toxUnitTest/", 1,
                                                     () -> new MyTestProcessRunner(),
                                                     Arrays.asList(
                                                       Pair.create("py27", new InterpreterExpectations("", true)),
                                                       // Does not support 3.4
                                                       Pair.create("py34", new InterpreterExpectations("SyntaxError", false))
                                                     ),
                                                     Integer.MAX_VALUE)
    );
  }

  /**
   * Checks empty envs for all but 2.7
   */
  @Test
  public void textToxOneInterpreter() {
    runPythonTest(new MyPyProcessWithConsoleTestTask("/toxtest/toxOneInterpreter/", 0,
                                                     () -> new MyTestProcessRunner(),
                                                     Arrays.asList(
                                                       Pair.create("py27", new InterpreterExpectations("ython 2.7", true)),
                                                       Pair.create("py34", new InterpreterExpectations("", false))
                                                     ),
                                                     Integer.MAX_VALUE)
    );
  }


  /**
   * Ensures test is not launched 2 times because folder added 2 times
   */
  @Test
  public void testDoubleRun() {
    runPythonTest(new MyPyProcessWithConsoleTestTask("/toxtest/toxDoubleRun/", 1,
                                                     () -> new MyTestProcessRunner(),
                                                     Collections.singletonList(
                                                       Pair.create("py27", new InterpreterExpectations("", true))
                                                     ),
                                                     1)
    );
  }

  /**
   * Big test which should run on any interpreter and check its output
   */
  @Test
  public void testToxSuccessTest() {
    runPythonTest(new MyPyProcessWithConsoleTestTask("/toxtest/toxSuccess/", 1,
                                                     () -> new MyTestProcessRunner(),
                                                     Arrays.asList(
                                                       Pair.create("py27", new InterpreterExpectations("I am 2.7", true)),
                                                       // Should have output
                                                       Pair.create("py34", new InterpreterExpectations("I am 3.4", true))
                                                     ),
                                                     Integer.MAX_VALUE)
    );
  }

  /**
   * Ensures rerun works for tox
   */
  @Test
  public void testEnvRerun() {
    runPythonTest(new MyPyProcessWithConsoleTestTask("/toxtest/toxConcreteEnv/", 0,
                                                     () -> new MyTestProcessRunner(1),
                                                     Arrays.asList(
                                                       //26 and 27 only used for first time, they aren't used after rerun
                                                       Pair.create("py27", new InterpreterExpectations("", true, 1)),
                                                       Pair.create("py34", new InterpreterExpectations("", false))
                                                     ),
                                                     Integer.MAX_VALUE)
    );
  }


  /**
   * Ensures {posargs} are passed correctly
   */
  @Test
  public void testToxPosArgs() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyAbstractTestProcessRunner<PyToxConfiguration>>("/toxtest/toxPosArgs/", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyAbstractTestProcessRunner<PyToxConfiguration> createProcessRunner() {
          return new MyTestProcessRunner() {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull final PyToxConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              PyToxTestTools.setArguments(configuration, "arg1");
            }
          };
        }


        @Override
        protected void checkTestResults(@NotNull final PyAbstractTestProcessRunner<PyToxConfiguration> runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {
          Assert.assertThat("Wrong sys.argv", stdout, Matchers.containsString("['spam.py', 'arg1']"));
        }

        @NotNull
        @Override
        public Set<String> getTags() {
          return Sets.newHashSet("tox");
        }
      });
  }

  /**
   * Provide certain env and check it is launched
   */
  @Test
  public void testConcreteEnv() {
    final String[] envsToRun = {"py27", "py34"};
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyAbstractTestProcessRunner<PyToxConfiguration>>("/toxtest/toxSuccess/", SdkCreationType.EMPTY_SDK) {
        @NotNull
        @Override
        protected PyAbstractTestProcessRunner<PyToxConfiguration> createProcessRunner() {
          return new PyAbstractTestProcessRunner<PyToxConfiguration>(PyToxConfigurationFactory.INSTANCE, PyToxConfiguration.class, 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull final PyToxConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              PyToxTestTools.setArguments(configuration, "-v");
              PyToxTestTools.setRunOnlyEnvs(configuration, envsToRun);
            }
          };
        }

        @Override
        protected void checkTestResults(@NotNull final PyAbstractTestProcessRunner<PyToxConfiguration> runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {
          final Set<String> environments = runner.getTestProxy().getChildren().stream().map(t -> t.getName()).collect(Collectors.toSet());
          assertThat(environments)
            .describedAs("Wrong environments launched")
            .containsExactly(envsToRun);
          assertThat(all).contains("-v");
        }

        @NotNull
        @Override
        public Set<String> getTags() {
          return Sets.newHashSet("tox");
        }
      });
  }


  private static final class MyPyProcessWithConsoleTestTask extends PyProcessWithConsoleTestTask<MyTestProcessRunner> {
    private static final Logger LOGGER = Logger.getInstance(MyPyProcessWithConsoleTestTask.class);
    @NotNull
    private final Map<String, InterpreterExpectations> myInterpreters = new HashMap<>();
    private final int myMinimumSuccessTestCount;
    private final int myMaximumSuccessTestCount;
    @NotNull
    private final Supplier<MyTestProcessRunner> myRunnerSupplier;

    /**
     * @param minimumSuccessTestCount how many success tests should be
     * @param interpreterExpectations interpreter_name -] expected result
     * @param runnerSupplier          Lambda to create runner (can't reuse one runner several times,
     *                                see {@link PyProcessWithConsoleTestTask#createProcessRunner()}
     * @param maximumTestCount        max number of success tests
     */
    private MyPyProcessWithConsoleTestTask(@Nullable final String relativeTestDataPath,
                                           final int minimumSuccessTestCount,
                                           @NotNull final Supplier<MyTestProcessRunner> runnerSupplier,
                                           @NotNull final Iterable<Pair<String, InterpreterExpectations>> interpreterExpectations,
                                           final int maximumTestCount) {
      super(relativeTestDataPath, SdkCreationType.EMPTY_SDK);
      myMinimumSuccessTestCount = minimumSuccessTestCount;
      myMaximumSuccessTestCount = maximumTestCount;
      myRunnerSupplier = runnerSupplier;
      for (final Pair<String, InterpreterExpectations> interpreterExpectation : interpreterExpectations) {
        myInterpreters.put(interpreterExpectation.first, interpreterExpectation.second);
      }
    }

    @Override
    protected void checkTestResults(@NotNull final MyTestProcessRunner runner,
                                    @NotNull final String stdout,
                                    @NotNull final String stderr,
                                    @NotNull final String all) {

      final Set<String> expectedInterpreters =
        myInterpreters.entrySet().stream()
                      .filter(intAndExp -> intAndExp.getValue() != null)
                      .filter(o -> o.getValue().myUntilStep >
                                   runner.getCurrentRerunStep()) // Remove interp. which shouldn't be launched on this step
                      .map(intAndExp -> intAndExp.getKey())
                      .collect(Collectors.toSet());

      // Interpreters are used in tox.ini, so there should be such text
      for (final String interpreterName : expectedInterpreters) {
        assertThat(all)
          .describedAs(String.format("No %s used from tox.ini", interpreterName))
          .contains(interpreterName);
      }

      if (!stderr.isEmpty()) {
        Logger.getInstance(PyToxTest.class).warn(PyEnvTestCase.escapeTestMessage(stderr));
      }


      final Set<String> checkedInterpreters = new HashSet<>();
      final Collection<String> skippedMissingInterpreters = new HashSet<>();
      // Interpreter should either run tests or mentioned as NotFound
      for (final SMTestProxy interpreterSuite : runner.getTestProxy().getChildren()) {
        final String interpreterName = interpreterSuite.getName();
        assert interpreterName.startsWith("py") : String
          .format("Bad interpreter name: %s. Tree is %s \n", interpreterName, getTestTree(interpreterSuite, 0));
        checkedInterpreters.add(interpreterName);

        final InterpreterExpectations expectations = myInterpreters.get(interpreterName);
        if (expectations == null) {
          LOGGER.warn(String.format("Launched %s, but no expectation provided, skipping", interpreterName));
          continue;
        }


        if (interpreterSuite.getChildren().size() == 1 && interpreterSuite.getChildren().get(0).getName().endsWith("ERROR")) {
          // Interpreter failed to run
          final String testOutput = getTestOutput(interpreterSuite.getChildren().get(0));
          if (testOutput.contains("InterpreterNotFound")) {
            // Skipped with out of "skip_missing_interpreters = True"
            Logger.getInstance(PyToxTest.class).warn(String.format("Interpreter %s does not exit", interpreterName));
            skippedMissingInterpreters.add(interpreterName); // Interpreter does not exit
            continue;
          }
          // Some other error?
          Assert
            .assertFalse(String.format("Interpreter %s should not fail, but failed: %s", interpreterName, getTestOutput(interpreterSuite)),
                         expectations.myExpectedSuccess);
          continue;
        }

        if (interpreterSuite.getChildren().size() == 1 && interpreterSuite.getChildren().get(0).getName().endsWith("SKIP")) {
          // The only reason it may be skipped is it does not exist and skip_missing_interpreters = True
          final String output = getTestOutput(interpreterSuite);
          assertThat(output)
            .describedAs("Test marked skipped but not because interpreter not found")
            .contains("InterpreterNotFound");
        }


        // Interpretr run success,
        //At least one interpreter tests should passed
        final int numberOfTests = new SMRootTestsCounter(interpreterSuite.getRoot()).getPassedTestsCount();
        assertThat(numberOfTests)
          .describedAs(String.format("Not enough test passed, should %s at least", myMinimumSuccessTestCount))
          .isGreaterThanOrEqualTo(myMinimumSuccessTestCount);
        assertThat(numberOfTests)
          .describedAs(String.format("Too many tests passed, should %s maximum", myMaximumSuccessTestCount))
          .isLessThanOrEqualTo(myMaximumSuccessTestCount);
        // Check expected output
        final String message = String.format("Interpreter %s does not have expected string in output. \n ", interpreterName) +
                               String.format("All: %s \n", all) +
                               String.format("Test tree: %s \n", getTestTree(interpreterSuite, 0)) +
                               String.format("Error: %s \n", stderr);


        assertThat(message)
          .describedAs(getTestOutput(interpreterSuite))
          .contains(expectations.myExpectedOutput);
      }

      // Skipped interpreters should not be checked since we do not know which interpreters used on environemnt
      // But if all interpreters are skipped, we can't say we tested something.
      assert !skippedMissingInterpreters.equals(checkedInterpreters) : "All interpreters skipped (they do not exist on platform), " +
                                                                       "we test nothing";
      expectedInterpreters.removeAll(skippedMissingInterpreters);
      checkedInterpreters.removeAll(skippedMissingInterpreters);

      assertThat(checkedInterpreters)
        .describedAs(String.format("No all interpreters from tox.ini used (test tree \n%s\n )", getTestTree(runner.getTestProxy(), 0)))
        .are(new Condition<String>() {
          @Override
          public boolean matches(String value) {
            return expectedInterpreters.contains(value);
          }
        });
    }

    @NotNull
    private static String getTestTree(@NotNull final SMTestProxy root, final int level) {
      final StringBuilder result = new StringBuilder();
      result.append(StringUtil.repeat(".", level)).append(root.getPresentableName()).append('\n');
      final Optional<String> children = root.getChildren().stream().map(o -> getTestTree(o, level + 1)).reduce((s, s2) -> s + s2);
      children.ifPresent(result::append);
      return result.toString();
    }

    @NotNull
    private static String getTestOutput(@NotNull final SMTestProxy test) {
      final MockPrinter p = new MockPrinter();
      test.printOn(p);
      return p.getAllOut();
    }

    @NotNull
    @Override
    public Set<String> getTags() {
      return Sets.newHashSet("tox");
    }

    @NotNull
    @Override
    protected MyTestProcessRunner createProcessRunner() {
      return myRunnerSupplier.get();
    }
  }

  private static class MyTestProcessRunner extends PyAbstractTestProcessRunner<PyToxConfiguration> {
    private MyTestProcessRunner() {
      this(0);
    }

    private MyTestProcessRunner(final int timesToRerunFailedTests) {
      super(PyToxConfigurationFactory.INSTANCE, PyToxConfiguration.class, timesToRerunFailedTests);
    }

    @Override
    protected void configurationCreatedAndWillLaunch(@NotNull PyToxConfiguration configuration) throws IOException {
      super.configurationCreatedAndWillLaunch(configuration);

      // To help tox with all interpreters, we add all our environments to path
      // Envs should have binaries like "python2.7" (with version included),
      // and tox will find em: see tox_get_python_executable @ interpreters.py

      //On linux we also need shared libs from Anaconda, so we add it to LD_LIBRARY_PATH
      final List<String> roots = new ArrayList<>();
      final List<String> libs = new ArrayList<>();
      for (final String root : getPythonRoots()) {
        File bin = new File(root, "/bin/");
        roots.add(bin.exists() ? bin.getAbsolutePath() : root);
        File lib = new File(root, "/lib/");
        if (lib.exists()) {
          libs.add(lib.getAbsolutePath());
        }
      }

      configuration.getEnvs().put("PATH", StringUtil.join(roots, File.pathSeparator));
      configuration.getEnvs().put("LD_LIBRARY_PATH", StringUtil.join(libs, File.pathSeparator));
    }
  }

  private static final class InterpreterExpectations {
    @NotNull
    private final String myExpectedOutput;
    private final boolean myExpectedSuccess;
    private final int myUntilStep;

    /**
     * @param expectedOutput  expected test output
     * @param expectedSuccess if test should be success
     * @param untilStep       in case of rerun, expectation works only until this step and not checked after it
     */
    private InterpreterExpectations(@NotNull final String expectedOutput, final boolean expectedSuccess, final int untilStep) {
      myExpectedOutput = expectedOutput;
      myExpectedSuccess = expectedSuccess;
      myUntilStep = untilStep;
    }

    /**
     * @see #InterpreterExpectations(String, boolean, int)
     */
    private InterpreterExpectations(@NotNull final String expectedOutput, final boolean expectedSuccess) {
      this(expectedOutput, expectedSuccess, Integer.MAX_VALUE);
    }
  }
}
