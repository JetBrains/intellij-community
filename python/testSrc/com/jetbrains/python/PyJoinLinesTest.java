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

import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.jetbrains.python.editor.PyJoinLinesHandler;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * Tests the "Join lines" handler.
 * <br/>
 * User: dcheryasov
 * Date: 1/29/11 2:33 AM
 */
public class PyJoinLinesTest extends PyTestCase {
  private void doTest() {
    final String path = "joinLines/";
    myFixture.configureByFile(path + getTestName(false) + ".py");
    // detect whitespace around EOL, the way JoinLinesHandler does.
    final Editor editor = myFixture.getEditor();
    final Document doc = editor.getDocument();
    final int caret_line = doc.getLineNumber(editor.getCaretModel().getOffset());
    final int eol_pos = doc.getLineEndOffset(caret_line);
    CharSequence text = doc.getCharsSequence();
    int i = eol_pos;
    while (" \n\t".indexOf(text.charAt(i)) >= 0) i -= 1;
    final int start = i+1;
    i = eol_pos;
    while (" \n\t".indexOf(text.charAt(i)) >= 0) i += 1;
    final int end = i;
    final JoinRawLinesHandlerDelegate handler = new PyJoinLinesHandler();
    new WriteAction() {
      @Override
      protected void run(Result result) throws Throwable {
        handler.tryJoinRawLines(doc, myFixture.getFile(), start, end);
      }
    }.execute().throwException();
    myFixture.checkResultByFile(path + getTestName(false) + "-after.py");
  }

  public void testBinaryOpBelow() { doTest(); }
  public void testBinaryOp() { doTest(); }
  public void testDictLCurly() { doTest(); }
  public void testDictRCurly() { doTest(); }
  public void testListLBracket() { doTest(); }
  public void testList() { doTest(); }
  public void testListRBracket() { doTest(); }
  public void testStatementColon() { doTest(); }
  public void testStatementComment() { doTest(); }
  public void testStatementCommentStatement() { doTest(); }
  public void testStringDifferentOneQuotes() { doTest(); }
  public void testStringDifferentOneQuotesBelow() { doTest(); }
  public void testStringOneQuoteEscEOL() { doTest(); }
  public void testStringOneQuotePlainRaw() { doTest(); }
  public void testStringOneQuotePlainU() { doTest(); }
  public void testStringTripleQuotesDifferent() { doTest(); }
  public void testStringTripleQuotes() { doTest(); }
  public void testTupleLPar() { doTest(); }
  public void testTuple() { doTest(); }
  public void testTupleRPar() { doTest(); }
  public void testTwoComments() { doTest(); }
  public void testTwoComments2() { doTest(); }   // PY-7286
  public void testTwoStatements() { doTest(); }
  public void testStringWithSlash() { doTest(); }
  public void testListOfStrings() { doTest(); }
  public void testLongExpression() { doTest(); }
  public void testListComprehension() { doTest(); }
}
