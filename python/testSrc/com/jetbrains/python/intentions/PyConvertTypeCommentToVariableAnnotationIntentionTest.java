/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

public class PyConvertTypeCommentToVariableAnnotationIntentionTest extends PyIntentionTestCase {
  private void doPositiveTest() {
    doTest(PyBundle.message("INTN.convert.type.comment.to.variable.annotation.text"), LanguageLevel.PYTHON36);
  }

  private void doNegativeTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> {
      doNegativeTest(PyBundle.message("INTN.convert.type.comment.to.variable.annotation.text"));
    });
  }

  public void testSimpleAssignment() {
    doPositiveTest();
  }

  public void testBadLanguageLevel() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> {
      doNegativeTest(PyBundle.message("INTN.convert.type.comment.to.variable.annotation.text"));
    });
  }

  public void testChainedAssignment() {
    doNegativeTest();
  }

  public void testAssignmentWithUnpacking() {
    doPositiveTest();
  }

  public void testAssignmentWithComplexUnpacking() {
    doPositiveTest();
  }

  public void testMultilineAssignment() {
    doPositiveTest();
  }

  public void testSimpleForLoop() {
    doPositiveTest();
  }

  public void testSimpleWithStatement() {
    doPositiveTest();
  }

  public void testForLoopWithUnpacking() {
    doPositiveTest();
  }

  public void testForLoopWithComplexUnpacking() {
    doPositiveTest();
  }

  public void testWithStatementWithUnpacking() {
    doPositiveTest();
  }

  public void testWithStatementWithComplexUnpacking() {
    doPositiveTest();
  }

  public void testWithStatementWithMultipleWithItems() {
    doNegativeTest();
  }

  // EA-116787
  public void testIllegalTypeHint() {
    doPositiveTest();
  }

  // PY-21195 EA-116787
  public void testTypeHintFollowedByComment() {
    doPositiveTest();
  }

  // EA-117868
  public void testIllegalTypeHintInAssignmentWithUnpacking() {
    doNegativeTest();
  }
}
