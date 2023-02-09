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
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyMethodOverridingInspection;
import org.jetbrains.annotations.NotNull;

public class PyMethodOverridingInspectionTest extends PyInspectionTestCase {
  public void testArgsKwargsOverrideArg() {
    doTest();
  }

  public void testNotOverridingMethod() {
    doTest();
  }

  public void testInitNew() {
    doTest();
  }

  public void testArgsKwargsAsAllowAnything() {
    doTest();
  }

  // PY-1083
  public void testExtraKwargs() {
    doTest();
  }

  // PY-6700
  public void testBothArgsKwargs() {
    doTest();
  }

  // PY-6700
  public void testArgAndKwargs() {
    doTest();
  }

  // PY-7157
  public void testDefaultArgument() {
    doTest();
  }

  // PY-7162
  public void testLessArgumentsPlusDefaults() {
    doTest();
  }

  public void testLessParametersAndKwargs() {
    doTest();
  }

  // PY-7159
  public void testRequiredParameterAndKwargs() {
    doTest();
  }

  // PY-7725
  public void testPropertySetter() {
    doTest();
  }

  // PY-10229
  public void testInstanceCheck() {
    doTest();
  }

  // PY-23513
  public void testOverriddingAbstractStaticMethodWithExpandedArguments() {
    doTest();
  }

  // PY-32556
  public void testOverriddingWithDecorator() {
    doTestByText("""
                   class BaseClass():
                       def method(self, arg1):
                           pass

                   def my_decorator(func):
                       pass

                   class Child(BaseClass):
                       @my_decorator
                       def method(self, arg1, arg2):
                           pass
                   """);
  }

  // PY-28506
  public void testDunderPostInitInDataclassHierarchy() {
    doMultiFileTest();
  }

  // PY-35512
  public void testPositionalOnlyParameters() {
    doTest();
  }

  // PY-17828
  public void testDunderPrepare() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyMethodOverridingInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }
}
