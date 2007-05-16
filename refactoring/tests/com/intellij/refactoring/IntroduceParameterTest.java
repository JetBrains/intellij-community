/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.05.2002
 * Time: 13:59:01
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import org.jetbrains.annotations.NonNls;
import gnu.trove.TIntArrayList;

public class IntroduceParameterTest extends CodeInsightTestCase {
  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal) throws Exception {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    perform(true, replaceFieldsWithGetters, "anObject", searchForSuper, declareFinal, removeUnusedParameters);
    checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
  }
  public void testNoUsages() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testSimpleUsage() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testMethodWithoutParams() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testParameterSubstitution() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testThisSubstitution() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testThisSubstitutionInQualifier() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testFieldAccess() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testMethodAccess() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testStaticFieldAccess() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testFieldWithGetterReplacement() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false, false);
  }

  public void testFieldWithInaccessibleGetterReplacement() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testWeirdQualifier() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testSuperInExpression() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testNull() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testWeirdQualifierAndParameter() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testImplicitSuperCall() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testImplicitDefaultConstructor() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testLocalVarDeclaration() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testInternalSideEffect() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testQualifiedNew() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testAnonymousClass() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testSuperWithSideEffect() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testConflictingField() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, false);
  }

  public void testParameterInFor() throws Exception {
    configureByFile("/refactoring/introduceParameter/beforeParameterInFor.java");
    performForLocal(true, true, true, false, false);
    checkResultByFile("/refactoring/introduceParameter/afterParameterInFor.java");
  }

  public void testParameterJavaDoc1() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDoc2() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDoc3() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDocBeforeVararg() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testIncorrectScope() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, true);
  }

  public void testExpectedType() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testRemoveParameter() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }

  public void testRemoveParameterInHierarchy() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }

  public void testRemoveParameterWithJavadoc() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }


  private boolean perform(boolean replaceAllOccurences,
                          int replaceFieldsWithGetters,
                          @NonNls String parameterName,
                          boolean searchForSuper, boolean declareFinal, final boolean removeUnusedParameters) {
    int startOffset = myEditor.getSelectionModel().getSelectionStart();
    int endOffset = myEditor.getSelectionModel().getSelectionEnd();

    PsiExpression expr = CodeInsightUtil.findExpressionInRange(myFile, startOffset, endOffset);

    if (expr == null) return false;

    PsiMethod method = Util.getContainingMethod(expr);
    if (method == null) return false;

    final PsiMethod methodToSearchFor;
    if (searchForSuper) {
      methodToSearchFor = method.findDeepestSuperMethod();
    }
    else {
      methodToSearchFor = method;
    }
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, expr) : new TIntArrayList();
    new IntroduceParameterProcessor(
      myProject, method, methodToSearchFor, expr, expr, null, true, parameterName, replaceAllOccurences,
      replaceFieldsWithGetters,
      declareFinal, null, parametersToRemove).run();

    myEditor.getSelectionModel().removeSelection();
    return true;
  }

  private void performForLocal(boolean searchForSuper, boolean removeLocalVariable, boolean replaceAllOccurences, boolean declareFinal,
                               final boolean removeUnusedParameters) {
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiElement element = myFile.findElementAt(offset).getParent();
    assertTrue(element instanceof PsiLocalVariable);
    PsiMethod method = Util.getContainingMethod(element);
    final PsiMethod methodToSearchFor;
    if (searchForSuper) {
      final PsiMethod deepestSuperMethod = method.findDeepestSuperMethod();
      methodToSearchFor = deepestSuperMethod != null ? deepestSuperMethod : method;
    }
    else {
      methodToSearchFor = method;
    }
    assertNotNull(method);
    assertNotNull(methodToSearchFor);
    final PsiLocalVariable localVariable = (PsiLocalVariable)element;
    final PsiExpression parameterInitializer = localVariable.getInitializer();
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, parameterInitializer) : new TIntArrayList();

    new IntroduceParameterProcessor(
      myProject, method, methodToSearchFor,
      parameterInitializer, null, localVariable, removeLocalVariable,
      localVariable.getName(), replaceAllOccurences,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      declareFinal, null, parametersToRemove).run();
  }
}
