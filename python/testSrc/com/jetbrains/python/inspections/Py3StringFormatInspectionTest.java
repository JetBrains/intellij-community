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

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class Py3StringFormatInspectionTest extends PyInspectionTestCase {
  public static final String TEST_DIRECTORY = "inspections/PyStringFormatInspection/";

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  // PY-16938
  public void testByteString() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testIndexElementWithPackedReferenceExpr() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testPackedDictLiteralInsideDictLiteral() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testPackedDictCallInsideDictLiteral() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testPackedListInsideList() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testPackedTupleInsideList() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testPackedTupleInsideTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testPackedListInsideTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testPackedRefInsideList()  {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testPackedRefInsideTuple()  {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-20599
  public void testPy3kAsciiFormatSpecifier() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest());
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
