
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.MockInlineMethodOptions;
import org.jetbrains.annotations.NonNls;

public class InlineMethodTest extends CodeInsightTestCase {
  private LanguageLevel myPreviousLanguageLevel;


  protected void setUp() throws Exception {
    super.setUp();
    myPreviousLanguageLevel = getJavaFacade().getEffectiveLanguageLevel();
    getJavaFacade().setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    getJavaFacade().setEffectiveLanguageLevel(myPreviousLanguageLevel);
    super.tearDown();
  }

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

  //This gives extra 'result' local variable, currently I don't see a way to cope with it, todo: think about addional inline possibilities
  //public void testLocalVariableResult() throws Exception { doTest(); }

  public void testSCR31093() throws Exception { doTest(); }

  public void testSCR37742() throws Exception { doTest(); }
  
  public void testChainingConstructor() throws Exception { doTest(); }

  public void testChainingConstructor1() throws Exception { doTest(); }

  public void testNestedCall() throws Exception { doTest(); }

  public void testIDEADEV3672() throws Exception { doTest(); }

  public void testIDEADEV5806() throws Exception { doTest(); }

  public void testIDEADEV6807() throws Exception { doTest(); }

  public void testIDEADEV12616() throws Exception { doTest(); }

  public void testVarargs() throws Exception { doTest(); }

  public void testVarargs1() throws Exception { doTest(); }

  public void testEnumConstructor() throws Exception { doTest(); }

  private void doTest() throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineMethod/" + name + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(fileName + ".after");
  }

  private void performAction() {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    assertTrue(element instanceof PsiMethod);
    final boolean condition = InlineMethodProcessor.checkBadReturns((PsiMethod) element);
    assertFalse("Bad returns found", condition);
    PsiMethod method = (PsiMethod)element;
    InlineOptions options = new MockInlineMethodOptions();
    final InlineMethodProcessor processor = new InlineMethodProcessor(myProject, method, refExpr, myEditor, options.isInlineThisOnly());
    processor.run();
  }
}
