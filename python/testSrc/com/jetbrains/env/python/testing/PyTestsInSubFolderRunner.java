/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.env.ut.PyScriptTestProcessRunner;
import com.jetbrains.python.psi.PyFunction;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * Checks tests are resolved when launched from subfolder
 */
abstract class PyTestsInSubFolderRunner<T extends PyScriptTestProcessRunner<?>> extends PyTestsFunctionBasedRunner<T> {

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
