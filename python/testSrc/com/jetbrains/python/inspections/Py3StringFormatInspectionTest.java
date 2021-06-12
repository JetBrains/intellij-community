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
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class Py3StringFormatInspectionTest extends PyInspectionTestCase {
  public static final String TEST_DIRECTORY = "inspections/PyStringFormatInspection/";

  // PY-16938
  public void testByteString() {
    doTest();
  }

  public void testIndexElementWithPackedReferenceExpr() {
    doTest();
  }

  public void testPackedDictLiteralInsideDictLiteral() {
    doTest();
  }

  public void testPackedDictCallInsideDictLiteral() {
    doTest();
  }

  public void testPackedListInsideList() {
    doTest();
  }

  public void testPackedTupleInsideList() {
    doTest();
  }

  public void testPackedTupleInsideTuple() {
    doTest();
  }

  public void testPackedListInsideTuple() {
    doTest();
  }

  public void testPackedRefInsideList() {
    doTest();
  }

  public void testPackedRefInsideTuple() {
    doTest();
  }

  // PY-20599
  public void testPy3kAsciiFormatSpecifier() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyStringFormatInspection.class;
  }

  @Override
  protected String getTestCaseDirectory() {
    return TEST_DIRECTORY;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }
}
