/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.command.WriteCommandAction;
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
    final PsiFile file = WriteCommandAction.runWriteCommandAction(null, new Computable<PsiFile>() {
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
