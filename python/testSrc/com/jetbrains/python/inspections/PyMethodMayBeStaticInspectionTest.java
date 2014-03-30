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

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;

import java.util.Arrays;

/**
 * User: ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/inspections/PyMethodMayBeStaticInspection/")
public class PyMethodMayBeStaticInspectionTest extends PyTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testEmpty() {
    doTest();
  }

  public void testInit() {
    doTest();
  }

  public void testWithQualifier() {
    doTest();
  }

  public void testStaticMethod() {
    doTest();
  }

  public void testClassMethod() {
    doTest();
  }

  public void testProperty() {
    doTest();
  }

  public void testSelfName() {
    doTest();
  }

  public void testNotImplemented() {
    doTest();
  }

  public void testDecorated() {
    doTest();
  }

  public void testOverwrittenMethod() {
    doTest();
  }

  public void testSuperMethod() {
    doTest();
  }

  public void testAbstractProperty() {
    doMultiFileTest("abc.py");
  }

  public void testPropertyWithAlias() {
    doMultiFileTest("abc.py");
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.enableInspections(PyMethodMayBeStaticInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  private void doMultiFileTest(String ... files) {
    String [] filenames = Arrays.copyOf(files, files.length + 1);
    filenames[files.length] = getTestName(true) + ".py";
    myFixture.configureByFiles(filenames);
    myFixture.enableInspections(PyMethodMayBeStaticInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/PyMethodMayBeStaticInspection/";
  }
}
