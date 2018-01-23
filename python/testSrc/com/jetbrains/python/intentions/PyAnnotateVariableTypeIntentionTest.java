// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyAnnotateVariableTypeIntentionTest extends PyIntentionTestCase {

  public void testNotSuggestedForLocalAssignmentTargetWithAnnotation() {
    doNegativeTest();
  }

  public void testNotSuggestedForLocalAssignmentTargetWithAheadOfTimeAnnotation() {
    doNegativeTest();
  }

  public void testNotSuggestedForLocalAssignmentTargetWithTypeComment() {
    doNegativeTest();
  }

  public void testNotSuggestedForComprehensionTarget() {
    doNegativeTest();
  }

  public void testNotSuggestedForImportTarget() {
    doNegativeTest();
  }

  public void testNotSuggestedForGlobalTarget() {
    doNegativeTest();
  }

  public void testNotSuggestedForNonlocalTarget() {
    doNegativeTest();
  }

  public void testNotSuggestedForLocalAssignmentTargetWithUnresolvedAnnotation() {
    doNegativeTest();
  }

  public void testNotSuggestedForLocalAssignmentTargetWithUnresolvedTypeComment() {
    doNegativeTest();
  }

  public void testNotSuggestedForLocalAssignmentTargetWithUnresolvedAheadOfTimeAnnotation() {
    doNegativeTest();
  }

  public void testNotSuggestedInstanceAttributeWithAnnotation() {
    doNegativeTest();
  }

  public void testNotSuggestedInstanceAttributeWithTypeComment() {
    doNegativeTest();
  }

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

  public void testAnnotationInstanceAttribute() {
    doTestAnnotation();
  }

  public void testAnnotationInstanceAttributeDocstring() {
    doTestAnnotation();
  }

  public void testAnnotationInstanceAttributeClassLevelAssignment() {
    doTestAnnotation();
  }

  public void testAnnotationInstanceAttributeClassLevelAssignmentInAncestor() {
    doTestAnnotation();
  }

  public void testTypeCommentInstanceAttribute() {
    doTestTypeComment();
  }

  public void testTypeCommentInstanceAttributePy3() {
    doTest(LanguageLevel.PYTHON30);
  }

  public void testTypeCommentInstanceAttributeDocstring() {
    doTestTypeComment();
  }

  public void testTypeCommentInstanceAttributeClassLevelAssignment() {
    doTestTypeComment();
  }

  public void testTypeCommentInstanceAttributeClassLevelAssignmentInAncestor() {
    doTestTypeComment();
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

  public void testTypeCommentComplexUnpackedAssignmentTarget() {
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

  public void testTypeCommentLocalSimpleAssignmentTargetWithExistingComment() {
    doTestTypeComment();
  }

  public void testTypeCommentLocalForTargetWithExistingComment() {
    doTestTypeComment();
  }

  public void testTypeCommentLocalWithTargetWithExistingComment() {
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

  private void doNegativeTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doNegativeTest(PyBundle.message("INTN.annotate.types")));
  }
}
