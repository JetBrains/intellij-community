// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
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
    doAnnotationTest();
  }

  public void testAnnotationLocalSimpleAssignmentTargetInParentheses() {
    doAnnotationTest();
  }

  public void testAnnotationLocalUnpackedAssignmentTarget() {
    doAnnotationTest();
  }

  public void testAnnotationLocalChainedAssignmentTarget() {
    doAnnotationTest();
  }

  public void testAnnotationLocalForTarget() {
    doAnnotationTest();
  }

  public void testAnnotationLocalWithTarget() {
    doAnnotationTest();
  }

  public void testAnnotationInstanceAttribute() {
    doAnnotationTest();
  }

  public void testAnnotationInstanceAttributeDocstring() {
    doAnnotationTest();
  }

  public void testAnnotationInstanceAttributeClassLevelAssignment() {
    doAnnotationTest();
  }

  public void testAnnotationInstanceAttributeClassLevelAssignmentInAncestor() {
    doAnnotationTest();
  }

  public void testTypeCommentInstanceAttribute() {
    doTypeCommentTest();
  }

  public void testTypeCommentInstanceAttributePy3() {
    doTest(LanguageLevel.PYTHON30);
  }

  public void testTypeCommentInstanceAttributeDocstring() {
    doTypeCommentTest();
  }

  public void testTypeCommentInstanceAttributeClassLevelAssignment() {
    doTypeCommentTest();
  }

  public void testTypeCommentInstanceAttributeClassLevelAssignmentInAncestor() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalSimpleAssignmentTarget() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalSimpleAssignmentTargetInParentheses() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalUnpackedAssignmentTarget() {
    doTypeCommentTest();
  }

  public void testTypeCommentComplexUnpackedAssignmentTarget() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalChainedAssignmentTarget() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalForTarget() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalWithTarget() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalSimpleAssignmentTargetWithExistingComment() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalForTargetWithExistingComment() {
    doTypeCommentTest();
  }

  public void testTypeCommentLocalWithTargetWithExistingComment() {
    doTypeCommentTest();
  }

  public void testAnnotationImportTypingAny() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationImportTypingUnion() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationImportTypingOptional() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationImportClassName() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationAugmentedAssignment() {
    doAnnotationTest();
  }

  public void testAnnotationMultiPathAugmentedAssignment() {
    doAnnotationTest();
  }

  public void testNotSuggestedForAugmentedAssignmentWithAmbiguousTarget() {
    doNegativeTest();
  }

  public void testNotSuggestedForUnresolvedAugmentedAssignmentTarget() {
    doNegativeTest();
  }

  public void testAnnotationCaretAtReferenceEnd() {
    doAnnotationTest();
  }

  public void testAnnotationCaretAtDefinitionNameEnd() {
    doAnnotationTest();
  }

  public void testAnnotationClassObjectType() {
    doAnnotationTest();
  }

  public void testAnnotationListType() {
    doAnnotationTest();
  }

  public void testAnnotationTupleType() {
    doAnnotationTest();
  }

  public void testAnnotationSameModuleClassNotImported() {
    doAnnotationTest();
  }

  public void testAnnotationNotPossibleForStructuralType() {
    doAnnotationTest();
  }

  public void testAnnotationNotPossibleForNestedStructuralType() {
    doAnnotationTest();
  }

  public void testAnnotationNotPossibleForStructuralTypeInCallable() {
    doAnnotationTest();
  }

  public void testAnnotationCallableType() {
    doAnnotationTest();
  }

  public void testAnnotationTypingNamedTupleInOtherFile() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationTypingNamedTupleClassInOtherFile() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationTypingNamedTupleDirectInheritorInOtherFile() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationTypeVarInOtherFile() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationCollectionsNamedTupleInOtherFile() {
    doMultiFileAnnotationTest();
  }

  public void testAnnotationCollectionsNamedTupleClassInOtherFile() {
    doMultiFileAnnotationTest();
  }

  public void testConflictWithAnnotationFunctionTypeIntention() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-28715
  public void testAnnotationObjectForVariableWithUnknownType() {
    doAnnotationTest();
  }

  // PY-28772
  public void testAnnotationGenericParametrizedWithAny() {
    doAnnotationTest();
  }

  // PY-28808
  public void testAnnotationEmptyTupleType() {
    doAnnotationTest();
  }

  private void doAnnotationTest() {
    doTest(LanguageLevel.PYTHON36);
  }

  private void doTypeCommentTest() {
    doTest(LanguageLevel.PYTHON27);
  }

  private void doNegativeTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doNegativeTest(PyBundle.message("INTN.add.type.hint.for.variable.family")));
  }

  public void doMultiFileAnnotationTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> {
      doMultiFileTest(PyBundle.message("INTN.add.type.hint.for.variable.family"));
    });
  }

  private void doMultiFileTest(@NotNull String hint) {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.py");
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    assertSdkRootsNotParsed(myFixture.getFile());
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(getTestName(false) + "/main_after.py", true);
  }

  private void doTest(@NotNull LanguageLevel languageLevel) {
    doTest(PyBundle.message("INTN.add.type.hint.for.variable.family"), languageLevel);
  }
}
