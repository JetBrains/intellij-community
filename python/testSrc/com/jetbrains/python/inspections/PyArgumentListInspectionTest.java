// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyArgumentListInspectionTest extends PyInspectionTestCase {
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
    doMultiFileTest("b.py");
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

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyArgumentListInspection.class;
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
    doMultiFileTest("b.py");
  }

  // PY-19522
  public void testCsvRegisterDialect() {
    doMultiFileTest("b.py");
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
    doMultiFileTest("b.py");
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
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doMultiFileTest("b.py"));
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedModule() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doMultiFileTest("b.py"));
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

  // PY-26023
  public void testAbstractMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  // PY-27398
  public void testInitializingDataclass() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, this::doMultiFileTest);
  }
}
