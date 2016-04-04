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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

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
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }
  
  public void testTupleVsLiteralList() {
    doTest();
  }
  
  public void testInheritedInit() {  // PY-312
    doTest();
  }
  
  public void testBadDecorator() {  // PY-428
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
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }
  
  public void testInstanceMethodAsLambda() {
    doTest();
  }

  public void testClassMethodMultipleDecorators() {
    doTest();
  }

  public void testTuples() {  // PY-2294
    doTest();
  }

  public void testNestedClass() {  // PY-2460
    doTest();
  }

  public void testReassignedMethod() {  // PY-2622
    doTest();
  }

  public void testConstructorQualifiedByModule() {
    doTest();
  }

  public void testFunctionStoredInInstance() {  // PY-3623
    doTest();
  }
  
  public void testUnresolvedSuperclass() {  // PY-4419
    doTest();
  }
  
  public void testMultipleInheritedConstructors() {  // PY-4897
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
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
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

  public void testParameterWithDefaultAfterKeywordContainer() {  // PY-9934
    doTest();
  }

  public void testParameterWithDefaultAfterKeywordContainer2() {  // PY-10351
    doTest();
  }

  public void testUnionTypeAttributeCall() {
    doTest();
  }

  // PY-18275
  public void testStrFormat() {
    doTest();
  }
}
