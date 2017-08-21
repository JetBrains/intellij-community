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

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyArgumentListInspectionTest extends PyTestCase {
  public void testBadarglist() {
    doTest();
  }
  
  public void testKwargsMapToNothing() {
    doTest();
  }
  
  public void testDecorators() {
    doTest();
  }

  public void testDecoratorsPy3K() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  // PY-19130
  public void testClassDecoratedThroughDecorator() {
    doTest();
  }

  // PY-19130
  public void testClassDecoratedThroughCall() {
    doTest();
  }
  
  public void testTupleVsLiteralList() {
    doTest();
  }

  // PY-312
  public void testInheritedInit() {
    doTest();
  }

  // PY-428
  public void testBadDecorator() {
    doTest();
  }
  
  public void testImplicitResolveResult() {
    doTest();
  }
  
  public void testCallingClassDefinition() {
    doTest();
  }
  
  public void testPy1133() {
    doTest();
  }
  
  public void testPy2005() {
    doTest();
  }
  
  public void testPy1268() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }
  
  public void testInstanceMethodAsLambda() {
    doTest();
  }

  public void testClassMethodMultipleDecorators() {
    doTest();
  }

  // PY-19412
  public void testReassignedViaClassMethod() {
    doTest();
  }

  // PY-19412
  public void testReassignedViaClassMethodInAnotherModule() {
    doMultiFileTest();
  }

  // PY-2294
  public void testTuples() {
    doTest();
  }

  // PY-2460
  public void testNestedClass() {
    doTest();
  }

  // PY-2622
  public void testReassignedMethod() {
    doTest();
  }

  public void testConstructorQualifiedByModule() {
    doTest();
  }

  // PY-3623
  public void testFunctionStoredInInstance() {
    doTest();
  }

  // PY-4419
  public void testUnresolvedSuperclass() {
    doTest();
  }

  // PY-4897
  public void testMultipleInheritedConstructors() {
    doTest();
  }

  public void testArgs() {
    doTest();
  }

  // PY-9080
  public void testMultipleInheritedConstructorsMRO() {
    doTest();
  }

  // PY-9978
  public void testXRange() {
    doTest();
  }

  // PY-9978
  public void testSlice() {
    doTest();
  }

  public void testPy3k() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyArgumentListInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyArgumentListInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  // PY-9664
  public void testFloatConstructor() {
    doTest();
  }

  // PY-10601
  public void testDecoratedChangedParameters() {
    doTest();
  }

  // PY-9605
  public void testPropertyReturnsCallable() {
    doTest();
  }

  // PY-11162
  public void testUnicodeConstructor() {
    doTest();
  }

  // PY-11169
  public void testDictFromKeys() {
    doTest();
  }

  // PY-9934
  public void testParameterWithDefaultAfterKeywordContainer() {
    doTest();
  }

  // PY-10351
  public void testParameterWithDefaultAfterKeywordContainer2() {
    doTest();
  }

  // PY-18275
  public void testStrFormat() {
    doTest();
  }

  // PY-19716
  public void testMethodsForLoggingExceptions() {
    doMultiFileTest();
  }

  // PY-19522
  public void testCsvRegisterDialect() {
    doMultiFileTest();
  }

  // PY-21083
  public void testFloatFromhex() {
    doTest();
  }

  public void testMultiResolveWhenOneResultIsDecoratedFunction() {
    doTest();
  }

  public void testMultiResolveWhenOneResultIsDunderInitInDecoratedClass() {
    // Implement after fixing PY-20057
  }

  public void testMultiResolveWhenOneResultDoesNotHaveUnmappedArguments() {
    doTest();
  }

  public void testMultiResolveWhenOneResultDoesNotHaveUnmappedParameters() {
    doTest();
  }

  public void testMultiResolveWhenAllResultsHaveUnmappedArguments() {
    doTest();
  }

  public void testMultiResolveWhenAllResultsHaveUnmappedParameters() {
    doTest();
  }

  public void testUnfilledSentinelInBuiltinIter() {
    doTest();
  }

  public void testUnfilledDefaultInBuiltinNext() {
    doTest();
  }

  public void testUnfilledIter4InBuiltinZip() {
    doTest();
  }

  public void testUnfilledIter2InBuiltinMap() {
    doTest();
  }

  // PY-22507
  public void testTimetupleOnAssertedDate() {
    doMultiFileTest();
  }

  // PY-23069
  public void testDunderNewCallInDictInheritor() {
    doTest();
  }

  // PY-22767
  public void testBuiltinZip() {
    doTest();
  }

  // PY-19293, PY-22102
  public void testInitializingTypingNamedTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-24099
  public void testInitializingTypingNamedTupleWithDefaultValues() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-4344, PY-8422, PY-22269, PY-22740
  public void testInitializingCollectionsNamedTuple() {
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

  // PY-22971
  public void testOverloadsAndImplementationInImportedClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doMultiFileTest);
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedModule() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doMultiFileTest);
  }

  public void testTypingCallableCall() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-24286
  public void testBuiltinLong() {
    doTest();
  }

  // PY-24930
  public void testCallOperator() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-16968
  public void testKwargsAgainstKeywordOnly() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  private void doMultiFileTest() {
    final String folderPath = "inspections/PyArgumentListInspection/" + getTestName(false) + "/";

    myFixture.copyDirectoryToProject(folderPath, "");
    myFixture.configureFromTempProjectFile("b.py");
    myFixture.enableInspections(PyArgumentListInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
