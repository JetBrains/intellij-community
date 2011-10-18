package com.jetbrains.python;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * Checks auto-unindenting of 'else' and friends.
 * User: dcheryasov
 * Date: Mar 5, 2010 2:49:38 PM
 */
public class PyAutoUnindentTest extends PyTestCase {

  public void testSingleElse() throws Exception{
    doTypingTest();
  }

  public void testNestedElse() throws Exception{
    doTypingTest();
  }

  public void testMisplacedElse() throws Exception{
    doTypingTest();
  }

  public void testSimpleElif() throws Exception{
    doTypingTest();
  }

  public void testInnerElif() throws Exception{
    doTypingTest();
  }

  public void testSimpleExcept() throws Exception{
    doTypingTest();
  }

  public void testSimpleFinally() throws Exception{
    doTypingTest();
  }

  public void testNestedFinally() throws Exception{
    doTypingTest();
  }

  /* does not complete keywords
  public void testNestedFinallyCompleted() throws Exception{
    doCompletionTest();
  }
  */



  private void doTypingTest() throws Exception {
    final String testName = "editing/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    doTyping(':');
    myFixture.checkResultByFile(testName + ".after.py");
  }

  private void doTyping(final char character) {
    final int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiFile file = ApplicationManager.getApplication().runWriteAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.type(character);
        return myFixture.getFile();
      }
    });
  }

  private void doCompletionTest() throws Exception {
    final String testName = "editing/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    LookupElement[] variants = myFixture.complete(CompletionType.SMART);
    myFixture.checkResultByFile(testName + ".after.py");
  }

}
