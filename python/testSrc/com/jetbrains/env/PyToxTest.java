/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.env;

import com.google.common.collect.Sets;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.tox.PyToxConfiguration;
import com.jetbrains.python.testing.tox.PyToxConfigurationFactory;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.*;

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
  public void testToxSimpleRun() {
    runPythonTest(new MyPyProcessWithConsoleTestTask(2,
                                                     new MyTestProcessRunner("/testData/toxtest/toxSimpleRun/"),
                                                     Arrays.asList(
                                                       // Should fail, no skip in 26
                                                       Pair.create("py26", new InterpreterExpectations(
                                                         "AttributeError: 'module' object has no attribute 'skip'", false)),
                                                       Pair.create("py27", new InterpreterExpectations("", true))
                                                     )
    ));
  }

  /**
   * Check tox nose runner
   */
  public void testToxNose() {
    runPythonTest(new MyPyProcessWithConsoleTestTask(1,
                                                     new MyTestProcessRunner("/testData/toxtest/toxNose/"),
                                                     Arrays.asList(
                                                       Pair.create("py26", new InterpreterExpectations("", true)),
                                                       Pair.create("py27", new InterpreterExpectations("", true)),
                                                       // Does not support 3.4
                                                       Pair.create("py32", new InterpreterExpectations("SyntaxError", false)),
                                                       Pair.create("py34", new InterpreterExpectations("SyntaxError", false))
                                                     )
                  )
    );
  }

  /**
   * Check tox pytest runner
   */
  public void testToxPyTest() {
    runPythonTest(new MyPyProcessWithConsoleTestTask(1,
                                                     new MyTestProcessRunner("/testData/toxtest/toxPyTest/"),
                                                     Arrays.asList(
                                                       Pair.create("py26", new InterpreterExpectations("", true)),
                                                       Pair.create("py27", new InterpreterExpectations("", true)),
                                                       // Does not support 3.4
                                                       Pair.create("py32", new InterpreterExpectations("SyntaxError", false)),
                                                       Pair.create("py34", new InterpreterExpectations("SyntaxError", false))
                                                     )
                  )
    );
  }

  /**
   * Check tox unit runner
   */
  public void testToxUnitTest() {
    runPythonTest(new MyPyProcessWithConsoleTestTask(1,
                                                     new MyTestProcessRunner("/testData/toxtest/toxUnitTest/"),
                                                     Arrays.asList(
                                                       Pair.create("py26", new InterpreterExpectations("", true)),
                                                       Pair.create("py27", new InterpreterExpectations("", true)),
                                                       // Does not support 3.4
                                                       Pair.create("py32", new InterpreterExpectations("SyntaxError", false)),
                                                       Pair.create("py34", new InterpreterExpectations("SyntaxError", false))
                                                     )
                  )
    );
  }

  /**
   * Big test which should run on any interpreter and check its output
   */
  public void testToxSuccessTest() {
    runPythonTest(new MyPyProcessWithConsoleTestTask(1,
                                                     new MyTestProcessRunner("/testData/toxtest/toxSuccess/"),
                                                     Arrays.asList(
                                                       Pair.create("py26", new InterpreterExpectations("I am 2.6", true)),
                                                       Pair.create("py27", new InterpreterExpectations("I am 2.7", true)),
                                                       // Should have output
                                                       Pair.create("py32", new InterpreterExpectations("I am 3.2", true)),
                                                       Pair.create("py34", new InterpreterExpectations("I am 3.4", true))
                                                     )
                  )
    );
  }


  private static final class MyPyProcessWithConsoleTestTask extends PyProcessWithConsoleTestTask<MyTestProcessRunner> {
    @NotNull
    private final Map<String, InterpreterExpectations> myInterpreters = new HashMap<>();
    private final int myMinimumSuccessTestCount;
    @NotNull
    private final MyTestProcessRunner myRunner;

    /**
     * @param minimumSuccessTestCount how many success tests should be
     * @param interpreterExpectations interpreter_name -] expected result
     */
    private MyPyProcessWithConsoleTestTask(final int minimumSuccessTestCount,
                                           @NotNull final MyTestProcessRunner runner,
                                           @NotNull final Collection<Pair<String, InterpreterExpectations>> interpreterExpectations) {
      super(SdkCreationType.SDK_PACKAGES_ONLY);
      myMinimumSuccessTestCount = minimumSuccessTestCount;
      myRunner = runner;
      for (final Pair<String, InterpreterExpectations> interpreterExpectation : interpreterExpectations) {
        myInterpreters.put(interpreterExpectation.first, interpreterExpectation.second);
      }
    }

    @Override
    protected void checkTestResults(@NotNull final MyTestProcessRunner runner,
                                    @NotNull final String stdout,
                                    @NotNull final String stderr,
                                    @NotNull final String all) {

      // Interpreters are used in tox.ini, so there should be such text
      for (final String interpreterName : myInterpreters.keySet()) {
        Assert.assertThat(String.format("No %s used from tox.ini", interpreterName), all, Matchers.containsString(interpreterName));
      }


      if (!stderr.isEmpty()) {
        Logger.getInstance(PyToxTest.class).warn(stderr);
      }


      final Set<String> checkedInterpreters = new HashSet<>();
      final Set<String> skippedInterpreters = new HashSet<>();
      // Interpreter should either run tests or mentioned as NotFound
      for (final SMTestProxy interpreterSuite : runner.getTestProxy().getChildren()) {
        final String interpreterName = interpreterSuite.getName();
        checkedInterpreters.add(interpreterName);

        if (interpreterSuite.getChildren().size() == 1 && interpreterSuite.getChildren().get(0).getName().endsWith("ERROR")) {
          // Interpreter failed to run
          final String testOutput = getTestOutput(interpreterSuite.getChildren().get(0));
          if (testOutput.contains("InterpreterNotFound")) {
            Logger.getInstance(PyToxTest.class).warn(String.format("Interpreter %s does not exit", interpreterName));
            skippedInterpreters.add(interpreterName); // Interpreter does not exit
            continue;
          }
          // Some other error?
          final InterpreterExpectations expectations = myInterpreters.get(interpreterName);
          Assert
            .assertFalse(String.format("Interpreter %s should not fail, but failed: %s", interpreterName, getTestOutput(interpreterSuite)),
                         expectations.myExpectedSuccess);
          continue;
        }

        // Interpretr run success,
        //At least one interpreter tests should passed
        Assert.assertThat(String.format("No test passed, should %s at least", myMinimumSuccessTestCount),
                          new SMRootTestsCounter(interpreterSuite.getRoot()).getPassedTestsCount(),
                          Matchers.greaterThanOrEqualTo(myMinimumSuccessTestCount));

        // Check expected output
        Assert
          .assertThat(String.format("Interpreter %s does not have expected string in output", interpreterName),
                      getTestOutput(interpreterSuite), Matchers.containsString(myInterpreters.get(interpreterName).myExpectedOutput));
      }

      Assert.assertThat("No all interpreters from tox.ini used", checkedInterpreters, Matchers.equalTo(myInterpreters.keySet()));
      assert !skippedInterpreters.equals(myInterpreters.keySet()) : "All interpreters skipped (they do not exist on platform), " +
                                                                    "we test nothing";
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
    protected MyTestProcessRunner createProcessRunner() throws Exception {
      return myRunner;
    }
  }

  private static final class MyTestProcessRunner extends PyAbstractTestProcessRunner<PyToxConfiguration> {
    /**
     * @param testPath testPath relative to community path
     */
    private MyTestProcessRunner(@NotNull final String testPath) {
      super(PyToxConfigurationFactory.INSTANCE, PyToxConfiguration.class,
            PythonHelpersLocator.getPythonCommunityPath() + testPath, 0);
    }
  }

  private static final class InterpreterExpectations {
    @NotNull
    private final String myExpectedOutput;
    private final boolean myExpectedSuccess;

    /**
     * @param expectedOutput  expected test output
     * @param expectedSuccess if test should be success
     */
    private InterpreterExpectations(@NotNull final String expectedOutput, final boolean expectedSuccess) {
      myExpectedOutput = expectedOutput;
      myExpectedSuccess = expectedSuccess;
    }
  }
}
