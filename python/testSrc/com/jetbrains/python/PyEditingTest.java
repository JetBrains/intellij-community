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

import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyEditingTest extends PyTestCase {
  public void testNoPairedParenthesesBeforeIdentifier() {       // PY-290
    assertEquals("(abc", doTestTyping("abc", 0, '('));
  }

  public void testPairedParenthesesAtEOF() {
    assertEquals("abc()", doTestTyping("abc", 3, '('));
  }

  public void testPairedQuotesInRawString() {   // PY-263
    assertEquals("r''", doTestTyping("r", 1, '\''));
  }

  public void testQuotesInString() {   // PY-5041
    assertEquals("'st''ring'", doTestTyping("'st'ring'", 3, '\''));
  }

  public void testNonClosingQuoteAtIdent() {   // PY-380
    assertEquals("'abc", doTestTyping("abc", 0, '\''));
  }

  public void testNonClosingQuoteAtNumber() {   // PY-380
    assertEquals("'123", doTestTyping("123", 0, '\''));
  }

  public void testAutoClosingQuoteAtRBracket() {
    assertEquals("'']", doTestTyping("]", 0, '\''));
  }

  public void testAutoClosingQuoteAtRParen() {
    assertEquals("'')", doTestTyping(")", 0, '\''));
  }

  public void testAutoClosingQuoteAtComma() {
    assertEquals("'',", doTestTyping(",", 0, '\''));
  }

  public void testAutoClosingQuoteAtSpace() {
    assertEquals("'' ", doTestTyping(" ", 0, '\''));
  }

  public void testNoClosingTriple() {
    assertEquals("'''", doTestTyping("''", 2, '\''));
  }

  public void testOvertypeFromInside() {
    assertEquals("''", doTestTyping("''", 1, '\''));
  }

  public void testGreedyBackspace() {  // PY-254
    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    boolean oldVSpaceValue = settings.isVirtualSpace();
    try {
      settings.setVirtualSpace(true);
      doTestBackspace("py254", new LogicalPosition(4, 8));
    }
    finally {
      settings.setVirtualSpace(oldVSpaceValue);
    }
  }

  public void testUnindentBackspace() {  // PY-853
    doTestBackspace("smartUnindent", new LogicalPosition(1, 4));
  }

  public void testUnindentTab() {  // PY-1270
    doTestBackspace("unindentTab", new LogicalPosition(4, 4));
  }

  private void doTestBackspace(final String fileName, final LogicalPosition pos) {
    myFixture.configureByFile("/editing/" + fileName + ".before.py");
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(pos);
    pressButton(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.checkResultByFile("/editing/" + fileName + ".after.py", true);
  }

  public void testUncommentWithSpace() throws Exception {   // PY-980
    myFixture.configureByFile("/editing/uncommentWithSpace.before.py");
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 1));
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        CommentByLineCommentAction action = new CommentByLineCommentAction();
        action.actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(), "", action.getTemplatePresentation(),
                                                 ActionManager.getInstance(), 0));
      }
    }, "", null);
    myFixture.checkResultByFile("/editing/uncommentWithSpace.after.py", true);
  }

  public void testEnterInLineComment() {  // PY-1739
    doTestEnter("# foo <caret>bar", "# foo \n# <caret>bar");
  }

  public void testEnterInPrefixString() {  // PY-5058
    doTestEnter("s = r'some <caret>string'", "s = r'some ' \\\n" +
                                            "    r'string'");
  }

  public void testEnterInStringFormatting() {  // PY-7039
    doTestEnter("foo += \"fooba<caret>r\" % foo\n",
                "foo += \"fooba\" \\\n" +
                "       \"r\" % foo\n");
  }

  public void testEnterInStatement() {
    doTestEnter("if a <caret>and b: pass", "if a \\\n        and b: pass");
  }

  public void testEnterBeforeStatement() {
    doTestEnter("def foo(): <caret>pass", "def foo(): \n    pass");
  }

  public void testEnterInParameterList() {
    doTestEnter("def foo(a,<caret>b): pass", "def foo(a,\n        b): pass");
  }

  public void testEnterInTuple() {
    doTestEnter("for x in 'a', <caret>'b': pass", "for x in 'a', \\\n         'b': pass");
  }

  public void testEnterInCodeWithErrorElements() {
    doTestEnter("z=1 <caret>2", "z=1 \n2");
  }

  public void testEnterAtStartOfComment() {  // PY-1958
    doTestEnter("# bar\n<caret># foo", "# bar\n\n# foo");
  }

  public void testEnterAtEndOfComment() {  // PY-1958
    doTestEnter("# bar<caret>\n# foo", "# bar\n\n# foo");
  }

  public void testEnterAfterBackslash() {  // PY-1960
    doTestEnter("s = \\<caret>\n'some string'", "s = \\\n\n'some string'");
  }

  public void testEnterBetweenCommentAndStatement() { // PY-1958
    doTestEnter("def test(a):\n <caret># some comment\n if a: return", "def test(a):\n \n # some comment\n if a: return");
  }

  public void testEnterBetweenDecoratorAndFunction() {  // PY-1985
    doTestEnter("@foo\n<caret>def bar(x): pass", "@foo\n\ndef bar(x): pass");
  }

  public void testEnterInSliceExpression() {  // PY-1992
    doTestEnter("a = some_list[<caret>slice_start:slice_end]", "a = some_list[\n    slice_start:slice_end]");
  }

  public void testEnterInSubscriptionExpression() {  // PY-1992
    doTestEnter("a = some_list[<caret>slice_start]", "a = some_list[\n    slice_start]");
  }

  public void testEnterBeforeComment() { // PY-2138
    doTestEnter("def x():\n    if foo():<caret>\n        #bar\n        baz()", "def x():\n    if foo():\n        \n        #bar\n        baz()");
  }

  public void testEnterInEmptyFile() {  // PY-2194
    doTestEnter(" <caret>\n", " \n \n");
  }

  public void testEnterInDocstring() {  // CR-PY-144
    doTestEnter(" def foo():\n  \"\"\" some comment<caret>\"\"\"\n  pass", " def foo():\n  \"\"\" some comment\n  \"\"\"\n  pass");
  }

  public void testEnterStubInDocstring() {  // CR-PY-144
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    final String oldFormat = documentationSettings.getFormat();
    documentationSettings.setFormat(DocStringFormat.PLAIN);
    try {
      doTestEnter("def foo():\n  \"\"\"<caret>", "def foo():\n" +
                                                 "  \"\"\"\n" +
                                                 "  \n" +
                                                 "  \"\"\"");
    } finally {
      documentationSettings.setFormat(oldFormat);
    }
  }

  public void testEnterInString() {  // PY-1738
    doTestEnter("a = \"some <caret>string\"", "a = \"some \" \\\n" +
                                              "    \"string\"");
  }

  public void testEnterInImportWithParens() {  // PY-2661
    doTestEnter("from django.http import (HttpResponse,<caret>)",
                "from django.http import (HttpResponse,\n" +
                "                         )");
  }

  public void testEnterInKeyword() {
    doTestEnter("imp<caret>ort django.http",
                "imp\n" +
                "ort django.http");
  }

  public void testEnterInIdentifier() {
    doTestEnter("import dja<caret>ngo.http",
                "import dja\n"+
                "ngo.http");
  }

  public void testEnterAfterStringPrefix() {
    doTestEnter("r<caret>\"string\"",
                "r\n"+
                "\"string\"");
  }

  public void testEnterInStringInParenth() {
    doTestEnter("a = (\"str<caret>ing\")",
                "a = (\"str\"\n" +
                "     \"ing\")");
  }

  public void testEnterEscapedQuote() {
    doTestEnter("a = 'some \\<caret>' string'",
                "a = 'some \\'' \\\n" +
                "    ' string'");
  }
  public void testEnterEscapedBackslash() {
    doTestEnter("a = 'some \\\\<caret> string'",
                "a = 'some \\\\' \\\n" +
                "    ' string'");
  }

  public void testEnterAfterSlash() {
    doTestEnter("a = 'some \\<caret> string'",
                "a = 'some \\\n" +
                " string'");
  }

  public void testStringFormatting() {
    doTestEnter("print (\"foo<caret> %s\" % 1)",
                "print (\"foo\"\n" +
                "       \" %s\" % 1)");
  }

  public void testEndOfStringInParenth() {
    doTestEnter("print (\"foo\"<caret>\n" +
                "    \"bar\")",
                "print (\"foo\"\n\n" +
                "    \"bar\")");
  }

  public void testSlashAfterSlash() {
    doTestEnter("a = a+\\<caret>b",
                "a = a+\\\n" +
                "    b");
  }

  public void testComprehensionInReturn() {
    doTestEnter("def dbl():\n" +
                "    return (<caret>(a, a) for a in [])",
                "def dbl():\n" +
                "    return (\n" +
                "        (a, a) for a in [])");
  }

  public void testParenthesizedInIf() {
    doTestEnter("if isinstance(bz_value, list) and <caret>(isinstance(bz_value[0], str)):\n" +
                "    pass",
                "if isinstance(bz_value, list) and \\\n" +
                "        (isinstance(bz_value[0], str)):\n" +
                "    pass");
  }

  public void testEmptyStringInParenthesis() {
    doTestEnter("a = ('<caret>')",
                "a = (''\n" +
                "     '')");
  }

  public void testEmptyStringInParenthesis2() {
    doTestEnter("a = (''\n" +
                "     <caret>'')",
                "a = (''\n" +
                "     \n" +
                "     '')");
  }

  public void testBracesInString() {
    doTestEnter("a = 'test(<caret>)'",
                "a = 'test(' \\\n" +
                "    ')'");
  }

  public void testEnterAfterDefKeywordInFunction() {
    doTestEnter("def <caret>func():\n" +
                "    pass",
                "def \\\n" +
                "        func():\n" +
                "    pass");
  }

  public void testEnterBeforeColonInFunction() {
    doTestEnter("def func()<caret>:\n" +
                "    pass",
                "def func()\\\n" +
                "        :\n" +
                "    pass");
  }

  // PY-15469
  public void testEnterBeforeArrowInFunction() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      public void run() {
        doTestEnter("def func() <caret>-> int:\n" +
                    "    pass",
                    "def func() \\\n" +
                    "        -> int:\n" +
                    "    pass");
      }
    });
  }

  // PY-15469
  public void testEnterAfterArrowInFunction() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      public void run() {
        doTestEnter("def func() -><caret> int:\n" +
                    "    pass",
                    "def func() ->\\\n" +
                    "        int:\n" +
                    "    pass");
      }
    });
  }

  // PY-15469
  public void testEnterDoesNotInsertSlashInsideArrow() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doTestEnter("def func() -<caret>> int:\n" +
                    "    pass",
                    "def func() -\n" +
                    "> int:\n" +
                    "    pass");
      }
    });
  }

  private void doTestEnter(String before, final String after) {
    int pos = before.indexOf("<caret>");
    before = before.replace("<caret>", "");
    doTestTyping(before, pos, '\n');
    myFixture.checkResult(after);
  }

  private String doTestTyping(final String text, final int offset, final char character) {
    final PsiFile file = WriteCommandAction.runWriteCommandAction(null, new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        final PsiFile file = myFixture.configureByText(PythonFileType.INSTANCE, text);
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.type(character);
        return file;
      }
    });
    return myFixture.getDocument(file).getText();
  }

  private void doTypingTest(final char character) {
    final String testName = "editing/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    doTyping(character);
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

  public void testFirstParamClassmethod() {
    doTypingTest('(');
  }

  public void testFirstParamMetaClass() {
    doTypingTest('(');
  }

  public void testFirstParamMetaNew() {
    doTypingTest('(');
  }

  public void testFirstParamMetaSimple() {
    doTypingTest('(');
  }

  public void testFirstParamSimpleInit() {
    doTypingTest('(');
  }

  public void testFirstParamSimpleNew() {
    doTypingTest('(');
  }

  public void testFirstParamSimple() {
    doTypingTest('(');
  }

  public void testFirstParamStaticmethod() {
    doTypingTest('(');
  }

  public void testFirstParamDuplicateColon() {  // PY-2652
    doTypingTest('(');
  }

  public void testEnterBeforeString() {  // PY-3673
    doTestEnter("<caret>''", "\n''");
  }

  public void testEnterInUnicodeString() {
    doTestEnter("a = u\"some <caret>text\"", "a = u\"some \" \\\n" +
                                         "    u\"<caret>text\"");
  }

  public void testBackslashInParenthesis() {  // PY-5106
    doTestEnter("(\"some <caret>string\", 1)", "(\"some \"\n" +
                                               " \"string\", 1)");
  }

  // PY-15609
  public void testEnterInStringInTupleWithoutParenthesis() {
    doTestEnter("def hello_world():\n" +
                "    return bar, 'so<caret>me'",
                "def hello_world():\n" +
                "    return bar, 'so' \\\n" +
                "                'me'");
  }
}
