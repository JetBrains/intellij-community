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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyUnusedLocalInspectionTest extends PyInspectionTestCase {

  public void testPy2() {
    final PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    inspection.ignoreTupleUnpacking = false;
    inspection.ignoreLambdaParameters = false;
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doTest(inspection));
  }

  public void testNonlocal() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  // PY-1235
  public void testTupleUnpacking() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, this::doTest);
  }

  // PY-959
  public void testUnusedFunction() {
    doTest();
  }

  // PY-9778
  public void testUnusedCoroutine() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doMultiFileTest("b.py"));
  }

  // PY-19491
  public void testArgsAndKwargsInDunderInit() {
    doTest();
  }

  // PY-20805
  public void testFStringReferences() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22087
  public void testFStringReferencesInComprehensions() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-8219
  public void testDoctestReference() {
    doTest();
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-23057
  public void testParameterInMethodWithEllipsis() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testSingleUnderscore() {
    doTest();
  }

  // PY-3996
  // PY-27435
  public void testUnderscorePrefixed() {
    doTest();
  }

  // PY-20655
  public void testCallingLocalsLeadsToUnusedParameter() {
    doTest();
  }

  // PY-28017
  public void testModuleGetAttr() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, this::doTest);
  }

  // PY-27435
  public void testVariableStartingWithUnderscore() {
    final PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    inspection.ignoreVariablesStartingWithUnderscore = false;
    doTest(inspection);
  }

  // PY-16419, PY-26417
  public void testPotentiallySuppressedExceptions() {
    doTestByText(
      "class C(object):\n" +
      "    def __enter__(self):\n" +
      "        return self\n" +
      "\n" +
      "    def __exit__(self, exc, value, traceback):\n" +
      "        return undefined\n" +
      "\n" +
      "def f11():\n" +
      "    with C():\n" +
      "        x = 1\n" +
      "        raise Exception()\n" +
      "    print(x) #pass\n" +
      "\n" +
      "def g2():\n" +
      "    raise Exception()\n" +
      "\n" +
      "def f12():\n" +
      "    with C():\n" +
      "        <weak_warning descr=\"Local variable 'x' value is not used\">x</weak_warning> = 2\n" +
      "        return g2()\n" +
      "    print(x) #pass\n" +
      "\n" +
      "class A1(TestCase):\n" +
      "    def f3(self):\n" +
      "        with C():\n" +
      "            x = 2\n" +
      "            g2()\n" +
      "        print(x) #pass\n" +
      "    \n" +
      "import contextlib\n" +
      "from contextlib import suppress\n" +
      "from unittest import TestCase\n" +
      "\n" +
      "def f21():\n" +
      "    with suppress(Exception):\n" +
      "        x = 1\n" +
      "        raise Exception()\n" +
      "    print(x) #pass\n" +
      "\n" +
      "def f22():\n" +
      "    with contextlib.suppress(Exception):\n" +
      "        x = 2\n" +
      "        return g2()\n" +
      "    print(x) #pass\n" +
      "\n" +
      "class A2(TestCase):\n" +
      "    def f3(self):\n" +
      "        with self.assertRaises(Exception):\n" +
      "            x = 2\n" +
      "            g2()\n" +
      "        print(x) #pass"
    );
  }
  // PY-22204
  public void testForwardTypeDeclaration() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22204
  public void testTypeDeclarationFollowsTargetBeforeItsFirstUsage() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyUnusedLocalInspection.class;
  }

  private void doTest(@NotNull PyUnusedLocalInspection inspection) {
    final String path = "inspections/PyUnusedLocalInspection/" + getTestName(true) + ".py";
    myFixture.configureByFile(path);
    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(true, false, true);
  }
}
