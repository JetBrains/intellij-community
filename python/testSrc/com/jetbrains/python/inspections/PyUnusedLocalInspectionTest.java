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
    doTest();
  }

  // PY-1235
  public void testTupleUnpacking() {
    doTest();
  }

  // PY-959
  public void testUnusedFunction() {
    doTest();
  }

  // PY-9778
  public void testUnusedCoroutine() {
    doMultiFileTest("b.py");
  }

  // PY-19491
  public void testArgsAndKwargsInDunderInit() {
    doTest();
  }

  // PY-20805
  public void testFStringReferences() {
    doTest();
  }

  // PY-22087
  public void testFStringReferencesInComprehensions() {
    doTest();
  }

  // PY-8219
  public void testDoctestReference() {
    doTest();
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    doTest();
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    doTest();
  }

  // PY-23057
  public void testParameterInMethodWithEllipsis() {
    doTest();
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
    doTest();
  }

  // PY-27435
  public void testVariableStartingWithUnderscore() {
    final PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    inspection.ignoreVariablesStartingWithUnderscore = false;
    doTest(inspection);
  }

  // PY-20893
  public void testExceptionTargetStartingWithUnderscore() {
    final PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    inspection.ignoreVariablesStartingWithUnderscore = true;
    doTest(inspection);
  }

  // PY-31388
  public void testIgnoringVariablesStartingWithUnderscore() {
    final PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    inspection.ignoreVariablesStartingWithUnderscore = true;
    inspection.ignoreLambdaParameters = false;
    inspection.ignoreLoopIterationVariables = false;
    inspection.ignoreTupleUnpacking = false;
    doTest(inspection);
  }
  
  // PY-79910
  public void testTryExceptInsideIfInsideFunction() {
    doTestByText("""
def test():
    num = 7
    if num < 10:
        try:
            next_num = input() # used
        except ValueError:
            next_num = None
    else:
        next_num = 0

    return next_num
        """
    );
  }

  // PY-16419, PY-26417
  public void testPotentiallySuppressedExceptions() {
    doTestByText(
      """
        class C(object):
            def __enter__(self):
                return self

            def __exit__(self, exc, value, traceback):
                return undefined

        def f11():
            with C():
                x = 1
                raise Exception()
            print(x) #pass

        def g2():
            raise Exception()

        def f12():
            with C():
                x = 2
                return g2()
            print(x) #pass

        class A1(TestCase):
            def f3(self):
                with C():
                    x = 2
                    g2()
                print(x) #pass
           \s
        import contextlib
        from contextlib import suppress
        from unittest import TestCase

        def f21():
            with suppress(Exception):
                x = 1
                raise Exception()
            print(x) #pass

        def f22():
            with contextlib.suppress(Exception):
                x = 2
                return g2()
            print(x) #pass

        class A2(TestCase):
            def f3(self):
                with self.assertRaises(Exception):
                    x = 2
                    g2()
                print(x) #pass"""
    );
  }
  // PY-22204
  public void testForwardTypeDeclaration() {
    doTest();
  }

  // PY-22204
  public void testTypeDeclarationFollowsTargetBeforeItsFirstUsage() {
    doTest();
  }

  // PY-44102
  public void testUnusedMultiAssignmentTarget() {
    doTest();
  }

  // PY-44102
  public void testUnusedAssignmentExpression() {
    doTest();
  }

  // PY-48760
  public void testAllBindingsOfSameNameInOrPatternConsideredUsed() {
    doTest();
  }

  // PY-48760
  public void testUnusedCapturePatterns() {
    doTest();
  }

  // PY-50943
  public void testIncompleteFunctionWithoutName() {
    doTest();
  }

  // PY-78662
  public void testUnusedTypeAliasReferredToInOtherTypeAlias() {
    doTest();
  }

  // PY-78663
  public void testUnusedTypeParameterInTypeAlias() {
    doTest();
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
