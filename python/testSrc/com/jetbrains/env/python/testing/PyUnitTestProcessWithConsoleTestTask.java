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

import com.intellij.execution.Location;
import com.intellij.execution.testframework.AbstractTestProxy;
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
import org.junit.Assert;

/**
 * {@link PyProcessWithConsoleTestTask} to be used with python unittest. It saves you from boilerplate
 * by setting working folder and creating {@link PyUnitTestProcessRunner}
 *
 * @author Ilya.Kazakevich
 */
abstract class PyUnitTestProcessWithConsoleTestTask extends PyProcessWithConsoleTestTask<PyUnitTestProcessRunner> {
  @NotNull
  protected final String myScriptName;

  PyUnitTestProcessWithConsoleTestTask(@NotNull final String relativePathToTestData, @NotNull final String scriptName) {
    super(relativePathToTestData, SdkCreationType.EMPTY_SDK);
    myScriptName = scriptName;
  }

  @NotNull
  @Override
  protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
    return new PyUnitTestProcessRunner(myScriptName, 0);
  }


  /**
   * Checks tests are resolved when launched from subfolder
   */
  abstract static class PyTestsInSubFolderRunner<T extends PyScriptTestProcessRunner<?>> extends PyProcessWithConsoleTestTask<T> {
    @NotNull
    private final String[] myFunctionsToCheck;

    /**
     * @param functionsToCheck name of functions that should be found in test tree and resolved
     */
    PyTestsInSubFolderRunner(@NotNull final String... functionsToCheck) {
      super("/testRunner/env/testsInFolder", SdkCreationType.EMPTY_SDK);
      myFunctionsToCheck = functionsToCheck.clone();
      assert functionsToCheck.length > 0 : "Provide functions";
    }


    @Override
    protected final void checkTestResults(@NotNull final T runner,
                                          @NotNull final String stdout,
                                          @NotNull final String stderr,
                                          @NotNull final String all) {
      for (final String function : myFunctionsToCheck) {
        checkMethod(runner, function);
      }
    }

    private void checkMethod(@NotNull final T runner, @NotNull final String functionName) throws AssertionError {

      ReadAction.run((ThrowableRunnable<AssertionError>)() -> {
        final AbstractTestProxy method = runner.findTestByName(functionName);
        final Location<?> methodLocation = method.getLocation(getProject(), GlobalSearchScope.moduleScope(myFixture.getModule()));
        Assert.assertNotNull("Failed to resolve method location", methodLocation);
        final PsiElement methodPsiElement = methodLocation.getPsiElement();
        Assert.assertNotNull("Failed to get PSI for method location", methodPsiElement);
        Assert.assertThat("Wrong test returned", methodPsiElement, Matchers.instanceOf(PyFunction.class));
        Assert.assertEquals("Wrong method name", functionName, ((PsiNamedElement)methodPsiElement).getName());
      });
    }
  }
}
