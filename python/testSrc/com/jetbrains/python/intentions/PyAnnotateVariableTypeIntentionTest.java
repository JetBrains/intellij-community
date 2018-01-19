// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyAnnotateVariableTypeIntentionTest extends PyIntentionTestCase {
  public void testAnnotationLocalSimpleAssignmentTarget() {
    doTestAnnotation();
  }

  public void testAnnotationLocalSimpleAssignmentTargetInParentheses() {
    doTestAnnotation();
  }

  public void testAnnotationLocalUnpackedAssignmentTarget() {
    doTestAnnotation();
  }

  public void testAnnotationLocalChainedAssignmentTarget() {
    doTestAnnotation();
  }

  public void testAnnotationLocalForTarget() {
    doTestAnnotation();
  }

  public void testAnnotationLocalWithTarget() {
    doTestAnnotation();
  }

  public void testTypeCommentLocalSimpleAssignmentTarget() {
    doTestTypeComment();
  }

  public void testTypeCommentLocalSimpleAssignmentTargetInParentheses() {
    doTestTypeComment();
  }

  public void testTypeCommentLocalUnpackedAssignmentTarget() {
    doTestTypeComment();
  }

  public void testTypeCommentLocalChainedAssignmentTarget() {
    doTestTypeComment();
  }

  public void testTypeCommentLocalForTarget() {
    doTestTypeComment();
  }

  public void testTypeCommentLocalWithTarget() {
    doTestTypeComment();
  }

  private void doTestAnnotation() {
    doTest(LanguageLevel.PYTHON36);
  }

  private void doTestTypeComment() {
    doTest(LanguageLevel.PYTHON27);
  }

  private void doTest(@NotNull LanguageLevel languageLevel) {
    doTest(PyBundle.message("INTN.annotate.types"), languageLevel);
  }
}
