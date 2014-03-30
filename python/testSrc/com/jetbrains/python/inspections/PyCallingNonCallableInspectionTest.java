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

/**
 * @author yole
 */
public class PyCallingNonCallableInspectionTest extends PyTestCase {
  public void testTupleNonCallable() {
    doTest();
  }
  
  public void testStaticMeth() {
    doTest();
  }
  
  public void testConcealer() {
    doTest();
  }
  
  public void testModule() {
    doTest();
  }
  
  public void testClassAsCallable() {  // PY-4061
    doTest();
  }
  
  public void testClassAssignments() {  // PY-4061
    doTest();
  }
  
  public void testNamedTupleCallable() {
    doTest();
  }

  // PY-3892
  public void testCallableCheck() {
    doTest();
  }
  
  public void testClassMethodFirstParam() {
    doTest();
  }

  // PY-4090
  public void testDecorators() {
    doTest();
  }

  // PY-4608
  public void testUnionType() {
    doTest();
  }

  // PY-8416
  public void testCallAttributeAssignment() {
    doTest();
  }

  // PY-5905
  public void testCallableClassDecorator() {
    doTest();
  }

  // PY-8182
  public void testGetattrCallable() {
    doTest();
  }

  // PY-8801
  public void testQualifiedNamedTuple() {
    doTest();
  }

  private void doTest() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    try {
      myFixture.configureByFile("inspections/PyCallingNonCallableInspection/" + getTestName(true) + ".py");
      myFixture.enableInspections(PyCallingNonCallableInspection.class);
      myFixture.checkHighlighting(true, false, false);
    }
    finally {
      setLanguageLevel(null);
    }
  }
}
