package com.intellij.refactoring.moveMethod;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author ven
 */
public class MoveInstanceMethodTest extends LightCodeInsightTestCase {

  public void testSimple() throws Exception { doTestForTargetParameter(true, 0); }

  public void testSimpleWithTargetField() throws Exception { doTestForTargetParameter(false, 1); }

  public void testInterface() throws Exception { doTestForTargetParameter(true, 0); }



  private void doTestForTargetParameter(boolean isTargetParameter, final int targetIndex) throws Exception {
    final String filePath = "/refactoring/moveInstanceMethod/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    String suggestedName = MoveInstanceMethodHandler.suggestParameterNameForThisClass(method.getContainingClass());
    final PsiVariable targetVariable = isTargetParameter ? ((PsiVariable)method.getParameterList().getParameters()[targetIndex]) :
                                       method.getContainingClass().getFields()[targetIndex];
    new MoveInstanceMethodProcessor(getProject(),
                                         method, targetVariable, null, suggestedName).testRun();
    checkResultByFile(filePath + ".after");

  }


}
