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
import com.intellij.idea.Bombed;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;

import java.util.Calendar;

public class IntroduceParameterTest extends CodeInsightTestCase {
  public void testNoUsages() throws Exception {
    configureByFile("/refactoring/introduceParameter/before01.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after01.java");
  }

  public void testSimpleUsage() throws Exception {
    configureByFile("/refactoring/introduceParameter/before02.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after02.java");
  }

  public void testMethodWithoutParams() throws Exception {
    configureByFile("/refactoring/introduceParameter/before03.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after03.java");
  }

  public void testParameterSubstitution() throws Exception {
    configureByFile("/refactoring/introduceParameter/before04.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after04.java");
  }

  public void testThisSubstitution() throws Exception {
    configureByFile("/refactoring/introduceParameter/before05.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after05.java");
  }

  public void testThisSubstitutionInQualifier() throws Exception {
    configureByFile("/refactoring/introduceParameter/before06.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after06.java");
  }

  public void testFieldAccess() throws Exception {
    configureByFile("/refactoring/introduceParameter/before07.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after07.java");
  }

  public void testMethodAccess() throws Exception {
    configureByFile("/refactoring/introduceParameter/before08.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after08.java");
  }

  public void testStaticFieldAccess() throws Exception {
    configureByFile("/refactoring/introduceParameter/before09.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after09.java");
  }

  public void testFieldWithGetterReplacement() throws Exception {
    configureByFile("/refactoring/introduceParameter/before10.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after10.java");
  }

  public void testFieldWithInaccessibleGetterReplacement() throws Exception {
    configureByFile("/refactoring/introduceParameter/before11.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after11.java");
  }

  public void testWeirdQualifier() throws Exception {
    configureByFile("/refactoring/introduceParameter/before12.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after12.java");
  }


  public void testSuperInExpression() throws Exception {
    configureByFile("/refactoring/introduceParameter/before13.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after13.java");
  }


  public void testNull() throws Exception {
    configureByFile("/refactoring/introduceParameter/before14.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after14.java");
  }

  public void testWeirdQualifierAndParameter() throws Exception {
    configureByFile("/refactoring/introduceParameter/before15.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after15.java");
  }

  public void testImplicitSuperCall() throws Exception {
    configureByFile("/refactoring/introduceParameter/before16.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after16.java");
  }

  public void testImplicitDefaultConstructor() throws Exception {
    configureByFile("/refactoring/introduceParameter/before17.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after17.java");
  }

  public void testLocalVarDeclaration() throws Exception {
    configureByFile("/refactoring/introduceParameter/before18.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after18.java");
  }

  public void testInternalSideEffect() throws Exception {
    configureByFile("/refactoring/introduceParameter/before19.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after19.java");
  }

  public void testQualifiedNew() throws Exception {
    configureByFile("/refactoring/introduceParameter/before20.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after20.java");
  }

  public void testAnonymousClass() throws Exception {
    configureByFile("/refactoring/introduceParameter/before21.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after21.java");
  }

  public void testSuperWithSideEffect() throws Exception {
    configureByFile("/refactoring/introduceParameter/before22.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, false);
    checkResultByFile("/refactoring/introduceParameter/after22.java");
  }

  public void testConflictingField() throws Exception {
    configureByFile("/refactoring/introduceParameter/before23.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "i", true, false);
    checkResultByFile("/refactoring/introduceParameter/after23.java");
  }

  public void testParameterInFor() throws Exception {
    configureByFile("/refactoring/introduceParameter/before24.java");
    performForLocal(true, true, true, false);
    checkResultByFile("/refactoring/introduceParameter/after24.java");
  }

  public void testParameterJavaDoc1() throws Exception {
    configureByFile("/refactoring/introduceParameter/before25.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, true);
    checkResultByFile("/refactoring/introduceParameter/after25.java");
  }

  @Bombed(user = "lesya", day = 30, month = Calendar.JUNE, description = "Need to fix javadoc formatter", year = 2006, time = 15)
  public void testParameterJavaDoc2() throws Exception {
    configureByFile("/refactoring/introduceParameter/before26.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, true);
    checkResultByFile("/refactoring/introduceParameter/after26.java");
  }

  public void testParameterJavaDoc3() throws Exception {
    configureByFile("/refactoring/introduceParameter/before27.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "anObject", false, true);
    checkResultByFile("/refactoring/introduceParameter/after27.java");
  }

  public void testIncorrectScope() throws Exception {
    configureByFile("/refactoring/introduceParameter/before29.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "myField", true, true);
    checkResultByFile("/refactoring/introduceParameter/after29.java");
  }

  public void testExpectedType() throws Exception {
    configureByFile("/refactoring/introduceParameter/before30.java");
    perform(true, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, "aString", false, true);
    checkResultByFile("/refactoring/introduceParameter/after30.java");
  }



  private boolean perform(boolean replaceAllOccurences,
                          int replaceFieldsWithGetters,
                          String parameterName,
                          boolean searchForSuper, boolean declareFinal) {
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
    new IntroduceParameterProcessor(
      myProject, method, methodToSearchFor, expr, expr, null, true, parameterName, replaceAllOccurences,
      replaceFieldsWithGetters,
      declareFinal, null).run();

    myEditor.getSelectionModel().removeSelection();
    return true;
  }

  private boolean performForLocal(boolean searchForSuper, boolean removeLocalVariable, boolean replaceAllOccurences, boolean declareFinal) {
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
    new IntroduceParameterProcessor(
      myProject, method, methodToSearchFor,
      parameterInitializer, null, localVariable, removeLocalVariable,
      localVariable.getName(), replaceAllOccurences,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      declareFinal, null).run();
    return true;
  }
}
