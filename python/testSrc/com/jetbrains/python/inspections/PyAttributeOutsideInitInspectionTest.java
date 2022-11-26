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

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PyAttributeOutsideInitInspectionTest extends PyInspectionTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testDefinedInSuperClass() {
    doTest();
  }

  public void testTestClass() {
    myFixture.configureByFile("packages/unittest/unittest.py");
    doTest();
  }

  public void testUnitTest() {
    myFixture.configureByFile("packages/unittest/unittest.py");
    doTest();
  }

  public void testFromSuperClassWithoutInit() {
    doTest();
  }

  public void testFromSuperHierarchy() {
    doTest();
  }

  public void testClassAttrs() {
    doTest();
  }

  public void testBaseClassAttrs() {
    doTest();
  }

  public void testInnerClass() {
    doTest();
  }

  public void testStaticMethod() {
    doTest();
  }

  public void testPrivateMethod() {
    doTest();
  }

  // PY-25263
  public void testProperty() {
    doTest();
  }

  // PY-25263
  public void testPropertyAnnotation() {
    doTest();
  }

  // PY-25263
  public void testPropertyNotSetInInit() {
    doTest();
  }

  // PY-32585
  public void testUpdatingInheritedProperty() {
    doTestByText("""
                   class Foo:
                       def __init__(self):
                           self._test = None

                       @property
                       def test(self):
                           return self._test

                       @test.setter
                       def test(self, value):
                           self._test = value

                   class Bar(Foo):
                       @property
                       def another_test(self):
                           return self.test

                       @another_test.setter
                       def another_test(self, value):
                           self.test = value""");
  }

  // PY-31049
  public void testAttributesOfProperty() {
    doTest();
  }

  // PY-31376
  public void testLocalVarInProperty() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyAttributeOutsideInitInspection.class;
  }
}
