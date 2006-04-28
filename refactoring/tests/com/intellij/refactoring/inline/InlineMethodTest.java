
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.MockInlineMethodOptions;

public class InlineMethodTest extends CodeInsightTestCase {

  public void testInlineParms() throws Exception {
    doTest();
  }

  public void testInlineWithQualifier() throws Exception {
    doTest();
  }

  public void testInlineWithQualifierFromSuper() throws Exception { doTest(); }
  public void testTry() throws Exception {
    doTest();
  }

  public void testTrySynchronized() throws Exception {
    doTest();
  }

  public void testSideEffect() throws Exception { doTest(); }

  public void testInlineWithTry() throws Exception { doTest(); }

  public void testVoidWithReturn() throws Exception { doTest(); }
  public void testVoidWithReturn1() throws Exception { doTest(); }

  public void testScr10884() throws Exception {
    doTest();
  }
  public void testFinalParameters() throws Exception { doTest(); }
  public void testFinalParameters1() throws Exception { doTest(); }

  public void testScr13831() throws Exception { doTest(); }

  public void testNameClash() throws Exception { doTest(); }

  public void testArrayAccess() throws Exception { doTest(); }

  public void testConflictingField() throws Exception { doTest(); }

  public void testCallInFor() throws Exception { doTest(); }

  public void testSCR20655() throws Exception { doTest(); }


  public void testFieldInitializer() throws Exception { doTest(); }

  public void testStaticFieldInitializer() throws Exception { doTest(); }
  public void testSCR22644() throws Exception { doTest(); }

  public void testCallUnderIf() throws Exception { doTest(); }

  public void testLocalVariableResult() throws Exception { doTest(); }

  public void testSCR31093() throws Exception { doTest(); }

  public void testSCR37742() throws Exception { doTest(); }
  
  public void testChainingConstructor() throws Exception { doTest(); }

  public void testNestedCall() throws Exception { doTest(); }

  public void testIDEADEV3672() throws Exception { doTest(); }

  public void testIDEADEV5806() throws Exception { doTest(); }

  public void testVarargs() throws Exception { doTest(); }

  private void doTest() throws Exception {
    String name = getTestName(false);
    String fileName = "/refactoring/inlineMethod/" + name + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(fileName + ".after");
  }

  private void performAction() throws Exception{
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? ((PsiReferenceExpression)ref) : null;
    assertTrue(element instanceof PsiMethod);
    final boolean condition = InlineMethodProcessor.checkBadReturns((PsiMethod) element);
    assertFalse("Bad returns found", condition);
    PsiMethod method = (PsiMethod)element;
    InlineOptions options = new MockInlineMethodOptions();
    final InlineMethodProcessor processor = new InlineMethodProcessor(myProject, method, refExpr, myEditor, options.isInlineThisOnly());
    processor.run();
  }
}
