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
package com.jetbrains.env.python.testing;

import com.google.common.collect.Sets;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyScriptTestProcessRunner;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.Set;

/**
 * {@link PyProcessWithConsoleTestTask} to be used with python unittest. It saves you from boilerplate
 * by setting working folder and creating {@link PyUnitTestProcessRunner}
 *
 * @author Ilya.Kazakevich
 */
abstract class PyUnitTestProcessWithConsoleTestTask extends PyProcessWithConsoleTestTask<PyUnitTestProcessRunner> {
  @NotNull
  protected final String myScriptName;
  private final int myRerunFailedTests;

  PyUnitTestProcessWithConsoleTestTask(@NotNull final String relativePathToTestData, @NotNull final String scriptName) {
    this(relativePathToTestData, scriptName, 0);
  }
  PyUnitTestProcessWithConsoleTestTask(@NotNull final String relativePathToTestData,
                                       @NotNull final String scriptName,
                                       final int rerunFailedTests) {
    super(relativePathToTestData, SdkCreationType.SDK_PACKAGES_ONLY);
    myScriptName = scriptName;
    myRerunFailedTests= rerunFailedTests;
  }

  @Nullable
  @Override
  public Set<String> getTagsToCover() {
    return Sets.newHashSet("python2.6", "python2.7", "python3.5", "python3.6", "jython", "pypy", "IronPython");
  }

  @NotNull
  @Override
  protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
    return new PyUnitTestProcessRunner(myScriptName, myRerunFailedTests);
  }


  /**
   * Checks each method by name
   */
  abstract static class PyTestsFunctionBasedRunner<T extends PyScriptTestProcessRunner<?>> extends PyProcessWithConsoleTestTask<T> {
    @NotNull
    protected final String[] myFunctionsToCheck;

    protected PyTestsFunctionBasedRunner(@NotNull final String... functionsToCheck) {
      super("/testRunner/env/testsInFolder", SdkCreationType.EMPTY_SDK);
      assert functionsToCheck.length > 0 : "Provide functions";
      myFunctionsToCheck = functionsToCheck.clone();
    }

    @Override
    protected final void checkTestResults(@NotNull final T runner,
                                          @NotNull final String stdout,
                                          @NotNull final String stderr,
                                          @NotNull final String all) {
      for (final String functionName : myFunctionsToCheck) {
        ReadAction.run((ThrowableRunnable<AssertionError>)() -> {
          final AbstractTestProxy method = runner.findTestByName(functionName);
          checkMethod(method, functionName);
        });
      }
    }

    /**
     * Called for each method
     */
    protected abstract void checkMethod(@NotNull final AbstractTestProxy method, @NotNull final String functionName);
  }

  /**
   * Checks tests are resolved when launched from subfolder
   */
  abstract static class PyTestsInSubFolderRunner<T extends PyScriptTestProcessRunner<?>> extends PyTestsFunctionBasedRunner<T> {

    /**
     * @param functionsToCheck name of functions that should be found in test tree and resolved
     */
    PyTestsInSubFolderRunner(@NotNull final String... functionsToCheck) {
      super(functionsToCheck);
    }


    @Override
    protected void checkMethod(@NotNull final AbstractTestProxy method, @NotNull final String functionName) {

      final Location<?> methodLocation = method.getLocation(getProject(), GlobalSearchScope.moduleScope(myFixture.getModule()));

      Assert.assertNotNull("Failed to resolve method location " + method, methodLocation);
      final PsiElement methodPsiElement = methodLocation.getPsiElement();
      Assert.assertNotNull("Failed to get PSI for method location", methodPsiElement);
      Assert.assertThat("Wrong test returned", methodPsiElement, Matchers.instanceOf(PyFunction.class));
      Assert.assertEquals("Wrong method name", functionName, ((PsiNamedElement)methodPsiElement).getName());
    }
  }

  /**
   * Checks test output is correct
   */
  abstract static class PyTestsOutputRunner<T extends PyScriptTestProcessRunner<?>> extends PyTestsFunctionBasedRunner<T> {

    PyTestsOutputRunner(@NotNull final String... functionsToCheck) {
      super(functionsToCheck);
    }

    @Override
    protected void checkMethod(@NotNull final AbstractTestProxy method, @NotNull final String functionName) {
      if (functionName.endsWith("test_metheggs")) {
        Assert.assertThat("Method output is broken",
                          MockPrinter.fillPrinter(method).getStdOut().trim(), Matchers.containsString("I am method"));
      }
      else if (functionName.endsWith("test_funeggs")) {
        Assert.assertThat("Function output is broken",
                          MockPrinter.fillPrinter(method).getStdOut().trim(), Matchers.containsString("I am function"));
      }
      else if (functionName.endsWith("test_first") || functionName.endsWith("test_second")) {
        // No output expected
      }
      else {
        throw new AssertionError("Unknown function " + functionName);
      }
    }
  }
}
