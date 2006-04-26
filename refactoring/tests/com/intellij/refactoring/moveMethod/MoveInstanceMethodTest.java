package com.intellij.refactoring.moveMethod;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.idea.Bombed;

import java.util.Calendar;

/**
 * @author ven
 */
public class MoveInstanceMethodTest extends LightCodeInsightTestCase {

  public void testSimple() throws Exception { doTest(true, 0); }

  public void testSimpleWithTargetField() throws Exception { doTest(false, 1); }

  public void testInterface() throws Exception { doTest(true, 0); }

  public void testWithInner() throws Exception { doTest(true, 0); }

  @Bombed(user = "lesya", day = 4, month = Calendar.MAY, description = "Need to fix javadoc formatter", year = 2006, time = 15)
  public void testJavadoc() throws Exception { doTest(true, 0); }

  public void testRecursive() throws Exception { doTest(true, 0); }

  public void testRecursive1() throws Exception { doTest(true, 0); }

  public void testQualifiedThis() throws Exception { doTest(true, 0); }

  public void testTwoParams() throws Exception { doTest(true, 0); }

  public void testNoThisParam() throws Exception { doTest(false, 0); }

  private void doTest(boolean isTargetParameter, final int targetIndex) throws Exception {
    final String filePath = "/refactoring/moveInstanceMethod/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    final PsiVariable targetVariable = isTargetParameter ? ((PsiVariable)method.getParameterList().getParameters()[targetIndex]) :
                                       method.getContainingClass().getFields()[targetIndex];
    new MoveInstanceMethodProcessor(getProject(),
                                    method, targetVariable, null, MoveInstanceMethodHandler.suggestParameterNames (method, targetVariable)).run();
    checkResultByFile(filePath + ".after");

  }


}
