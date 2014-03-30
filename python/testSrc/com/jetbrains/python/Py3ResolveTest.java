/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author yole
 */
public class Py3ResolveTest extends PyResolveTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PyTestCase.ourPy3Descriptor;
  }

  @Override
  protected PsiElement doResolve() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    final PsiReference ref = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
    return ref.resolve();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON32);
  }

  @Override
  protected void tearDown() throws Exception {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    super.tearDown();
  }

  public void testObjectMethods() {  // PY-1494
    assertResolvesTo(PyFunction.class, "__repr__");
  }

  // PY-5499
  public void testTrueDiv() {
    assertResolvesTo(PyFunction.class, "__truediv__");
  }
}
