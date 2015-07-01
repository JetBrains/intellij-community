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
package com.jetbrains.jython;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.inspections.PyCallingNonCallableInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;

/**
 * @author yole
 */
public class PyJythonHighlightingTest extends LightCodeInsightFixtureTestCase {
  public void testCallableJavaClass() {
    doCallableTest();
  }

  public void testCallableStaticMethod() {
    doCallableTest();
  }

  private void doCallableTest() {
    myFixture.configureByFile(getTestName(false) + ".py");
    myFixture.enableInspections(PyCallingNonCallableInspection.class, PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }


  @Override
  protected String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/testData/highlighting/jython/";
  }
}
