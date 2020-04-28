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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

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

  // PY-1779
  public void testAutoCloseTriple() {
    assertEquals("''''''", doTestTyping("''", 2, '\''));
  }

  // PY-1779
  public void testAutoRemoveTriple() {
    doTestBackspace("closedTripleQuoteBackspace", new LogicalPosition(1, 3));
  }
  
  // PY-19084
  public void testNoAoutoclosingAtTheEnd() {
    assertEquals("'''docstring'''", doTestTyping("'''docstring''", 14,  '\''));
  }

  public void testAutoCloseAfterIllegalPrefix() {
    assertEquals("rrr''", doTestTyping("rrr", 3, '\''));
  }

  // PY-18972
  public void testFStringAutomaticallyInsertedClosingQuotes() {
    assertEquals("f''", doTestTyping("f", 1, '\''));
    assertEquals("rf''", doTestTyping("rf", 2, '\''));
    assertEquals("fr''", doTestTyping("fr", 2, '\''));
    assertEquals("fr''''''", doTestTyping("fr''", 4, "'"));
    assertEquals("fr''''''", doTestTyping("fr''", 3, "''"));
  }

  // PY-32872
  public void testClosingQuotesCompletionForTripleQuotedFString() {
    assertEquals("f''''''\n", doTestTyping("f''\n", 3, "'"));
  }

  // PY-33901
  public void testFStringManuallyInsertedClosingQuotes() {
    assertEquals("f'foo'", doTestTyping("f'foo", 5, "'"));
    assertEquals("f'''foo'''", doTestTyping("f'''foo''", 9, "'"));
  }

  // PY-35434
  public void testFStringQuoteInTextPartNotDuplicated() {
    assertEquals("f'\"]'", doTestTyping("f']'", 2, '"'));
    assertEquals("f'\" '", doTestTyping("f' '", 2, '"'));
    assertEquals("f'''foo\" '''", doTestTyping("f'''foo '''", 7, '"'));
    assertEquals("f'''foo\"\n'''", doTestTyping("f'''foo\n'''", 7, '"'));
  }

  // PY-35461
  public void testFStringAutomaticClosingQuotesRemoval() {
    doTestBackspace("f'<caret>'", "f");
    doTestBackspace("f'''<caret>'''", "f");
  }

  public void testNoClosingQuotesAfterTripleQuotesInsideTripleQuotedFString() {
    assertEquals("f'''\"\"\"@'''", doTestTyping("f'''\"\"@'''", 6, "\""));
  }

  public void testFStringFragmentBraces() {
    assertEquals("f'{}'", doTestTyping("f''", 2, '{'));
  }

  public void testEnterInMultilineFStringFragment() {
    doTestEnter("f'''{1 +<caret> 2}'''",
                "f'''{1 +\n" +
                "     2}'''");
  }

  public void testEnterInSingleLineFStringFragment() {
    doTestEnter("f'foo{1 +<caret> 2}bar'",
                "f'foo{1 +\n" +
                "2}bar'");
  }

  public void testEnterInFStringTextPart() {
    doTestEnter("f'foo<caret>bar'", "f'foo' \\\n" +
                                    "f'bar'");
  }

  // PY-31984
  public void testEnterInFStringRightBeforeFragment() {
    doTestEnter("f'foo<caret>{42}bar'", "f'foo' \\\n" +
                                        "f'{42}bar'");
  }

  // PY-32918
  public void testEnterInFStringRightBeforeClosingQuote() {
    doTestEnter("(f'foo{42}bar<caret>')", "(f'foo{42}bar'\n" +
                                          " f'')");
  }

  // PY-32873
  public void testEnterInTripleQuotedFStringRightBeforeClosingQuotes() {
    doTestEnter("f\"\"\"<caret>\"\"\"", "f\"\"\"\n" +
                                        "<caret>\"\"\"");
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

  private void doTestBackspace(final String before, final String after) {
    myFixture.configureByText(PythonFileType.INSTANCE, before);
    pressButton(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.checkResult(after);
  }

  public void testUncommentWithSpace() {   // PY-980
    myFixture.configureByFile("/editing/uncommentWithSpace.before.py");
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 1));
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);
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
    runWithDocStringFormat(DocStringFormat.PLAIN, () -> doTestEnter("def foo():\n  \"\"\"<caret>", "def foo():\n" +
                                                                                               "  \"\"\"\n" +
                                                                                               "  \n" +
                                                                                               "  \"\"\""));
  }

  // PY-18486
  public void testTripleQuotesThenEnterInsertsDocstring() {
    doDocStringTypingTest("\"\"\"\n", DocStringFormat.REST);
  }

  public void testEnterDocStringStubInClass() {
    doDocStringTypingTest("\n", DocStringFormat.REST);
  }

  public void testEnterDocStringStubInFile() {
    doDocStringTypingTest("\n", DocStringFormat.REST);
  }

  // PY-16656
  public void testEnterDocStringStubInFunctionWithSelf() {
    doDocStringTypingTest("\n", DocStringFormat.REST);
  }
  
  // PY-16656
  public void testEnterDocStringStubInStaticMethodWithSelf() {
    doDocStringTypingTest("\n", DocStringFormat.REST);
  }

  // PY-16828
  public void testEnterDocStringStubWithStringPrefix() {
    doDocStringTypingTest("\n", DocStringFormat.REST);
  }

  // PY-3421
  public void testSpaceDocStringStubInFunction() {
    doDocStringTypingTest(" ", DocStringFormat.REST);
  }

  // PY-3421
  public void testSpaceDocStringStubInFile() {
    doDocStringTypingTest(" ", DocStringFormat.REST);
  }

  // PY-3421
  public void testSpaceDocStringStubInClass() {
    doDocStringTypingTest(" ", DocStringFormat.REST);
  }

  // PY-16765
  public void testSectionIndentInsideGoogleDocString() {
    doDocStringTypingTest("\nparam", DocStringFormat.GOOGLE);
  }

  // PY-16765
  public void testSectionIndentInsideGoogleDocStringCustomIndent() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocStringTypingTest("\nparam", DocStringFormat.GOOGLE);
  }

  // PY-17183
  public void testEnterDocstringStubWhenFunctionDocstringBelow() {
    doDocStringTypingTest("\n", DocStringFormat.GOOGLE);
  }
  
  // PY-17183
  public void testEnterDocstringStubWhenClassDocstringBelow() {
    doDocStringTypingTest("\n", DocStringFormat.GOOGLE);
  }

  // PY-17183
  public void testEnterNoDocstringStubWhenCodeExampleInDocstring() {
    doDocStringTypingTest("\n", DocStringFormat.GOOGLE);
  }

  // PY-15332
  public void testEnterDocstringStubNoReturnTagForInit() {
    doDocStringTypingTest("\n", DocStringFormat.REST);
  }

  // PY-15532
  public void testSpaceDocstringStubNoReturnSectionForInit() {
    final PyCodeInsightSettings codeInsightSettings = PyCodeInsightSettings.getInstance();
    final boolean oldInsertTypeDocStub = codeInsightSettings.INSERT_TYPE_DOCSTUB;
    codeInsightSettings.INSERT_TYPE_DOCSTUB = true;
    try {
      doDocStringTypingTest(" ", DocStringFormat.GOOGLE);
    }
    finally {
      codeInsightSettings.INSERT_TYPE_DOCSTUB = oldInsertTypeDocStub;
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
                "print (\"foo\"\n" +
                "       \n" +
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
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doTestEnter("def func() <caret>-> int:\n" +
                                                               "    pass",
                "def func() \\\n" +
                "        -> int:\n" +
                "    pass"));
  }

  // PY-15469
  public void testEnterAfterArrowInFunction() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doTestEnter("def func() -><caret> int:\n" +
                                                               "    pass",
                "def func() ->\\\n" +
                "        int:\n" +
                "    pass"));
  }

  // PY-15469
  public void testEnterDoesNotInsertSlashInsideArrow() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doTestEnter("def func() -<caret>> int:\n" +
                                                               "    pass",
                "def func() -\n" +
                "> int:\n" +
                "    pass"));
  }

  private void doTestEnter(String before, final String after) {
    int pos = before.indexOf("<caret>");
    before = before.replace("<caret>", "");
    doTestTyping(before, pos, '\n');
    myFixture.checkResult(after);
  }

  // PY-21478
  public void testContinuationIndentForFunctionArguments() {
    getPythonCodeStyleSettings().USE_CONTINUATION_INDENT_FOR_ARGUMENTS = true;
    doTestEnter("func(<caret>)",
                "func(\n" +
                "        <caret>\n" +
                ")");
  }

  // PY-20909
  public void testContinuationIndentInEmptyListLiteral() {
    getPythonCodeStyleSettings().USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = true;
    doTestEnter("[<caret>]",
                "[\n" +
                "        <caret>\n" +
                "]");
  }

  // PY-20909
  public void testContinuationIndentInEmptyDictLiteral() {
    getPythonCodeStyleSettings().USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = true;
    doTestEnter("{<caret>}",
                "{\n" +
                "        <caret>\n" +
                "}");
  }

  // PY-20909
  public void testContinuationIndentInEmptyTupleLiteral() {
    getPythonCodeStyleSettings().USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = true;
    doTestEnter("(<caret>)",
                "(\n" +
                "        <caret>\n" +
                ")");
  }

  // PY-21840
  public void testEditInjectedRegexpFragmentWithLongUnicodeEscape() {
    myFixture.configureByText(PythonFileType.INSTANCE,
                              "import re\n" +
                              "re.compile(ur'\\U00010000<caret>')");
    myFixture.type("t");
    myFixture.checkResult("import re\n" +
                          "re.compile(ur'\\U00010000t')");
  }

  // PY-21697
  public void testTripleQuotesInsideTripleQuotedStringLiteral() {
    // TODO an extra quote is inserted due to PY-21993
    doTypingTest("'");
  }

  private String doTestTyping(final String text, final int offset, final char character) {
    final PsiFile file = myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.getEditor().getCaretModel().moveToOffset(offset);
    myFixture.type(character);
    return myFixture.getDocument(file).getText();
  }

  private String doTestTyping(final String text, final int offset, final String characters) {
    final PsiFile file = myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.getEditor().getCaretModel().moveToOffset(offset);
    myFixture.type(characters);
    return myFixture.getDocument(file).getText();
  }

  private void doTestTyping(@NotNull String before, @NotNull String typedText, @NotNull String after) {
    myFixture.configureByText("a.py", before);
    myFixture.type(typedText);
    myFixture.checkResult(after);
  }

  private void doTypingTest(final char character) {
    final String testName = "editing/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.type(character);
    myFixture.checkResultByFile(testName + ".after.py");
  }

  private void doTypingTest(@NotNull String text) {
    final String testName = "editing/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.type(text);
    myFixture.checkResultByFile(testName + ".after.py");
  }

  private void doDocStringTypingTest(final String text, @NotNull DocStringFormat format) {
    runWithDocStringFormat(format, () -> doTypingTest(text));
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

  // PY-21269
  public void testFirstParamMultipleMethods() {
    doTypingTest('(');
  }

  // PY-15240
  public void testFirstParamSpacesInsideParentheses() {
    getCommonCodeStyleSettings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    doTypingTest('(');
  }

  // PY-15240
  public void testFirstParamSpacesInsideEmptyParentheses() {
    getCommonCodeStyleSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = true;
    doTypingTest('(');
  }

  // PY-21289
  public void testPairedParenthesesMultipleCalls() {
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

  // PY-27178
  public void testIncompleteFunctionTypeComment() {
    doTypingTest('.');
  }

  // PY-10972
  public void testEnterInIncompleteTupleLiteral() {
    doTypingTest("\n'baz'");
  }

  // PY-10972
  public void testEnterInIncompleteListLiteral() {
    doTypingTest("\n'baz'");
  }

  // PY-10972
  public void testEnterInIncompleteSetLiteral() {
    doTypingTest("\n'baz'");
  }

  // PY-10972
  public void testEnterInIncompleteDictLiteral() {
    doTypingTest("\n'baz'");
  }

  // PY-10972
  public void testEnterInIncompleteGluedStringLiteralInParentheses() {
    doTypingTest("\n'bar'");
  }

  // PY-10972
  public void testEnterInIncompleteListComprehension() {
    doTypingTest("\nfoo");
  }

  // PY-10972
  public void testEnterInIncompleteSetComprehension() {
    doTypingTest("\nfoo");
  }

  // PY-10972
  public void testEnterInIncompleteDictComprehension() {
    doTypingTest("\nfoo");
  }

  // PY-10972
  public void testEnterInIncompleteParenthesizedGenerator() {
    doTypingTest("\nfoo");
  }

  // PY-10972
  public void testEnterInIncompleteNestedListLiteral() {
    doTypingTest("\n'baz'");
  }

  // PY-10972
  public void testEnterInIncompleteNestedTupleLiteral() {
    doTypingTest("\n'baz'");
  }

  // PY-10972
  public void testEnterInIncompleteNestedGluedStringInParentheses() {
    doTypingTest("\n'baz'");
  }

  public void testTabOutFromStringLiteral() {
    boolean savedValue = CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES;
    CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES = true;
    try {
      myFixture.configureByText(getTestName(true) + ".py",
                                "def some():\n" +
                                "    print<caret>");
      myFixture.type("(\"");
      myFixture.performEditorAction(IdeActions.ACTION_BRACE_OR_QUOTE_OUT);
      myFixture.checkResult("def some():\n" +
                            "    print(\"\"<caret>)");
      myFixture.performEditorAction(IdeActions.ACTION_BRACE_OR_QUOTE_OUT);
      myFixture.checkResult("def some():\n" +
                            "    print(\"\")<caret>");
    }
    finally {
      CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES = savedValue;
    }
  }

  // PY-31343
  public void testInsertingColonRightBeforeParametersClosingParenthesisAtMultipleCarets() {
    doTestTyping("def alpha(foo<caret>):\n" +
                 "    pass\n" +
                 "\n" +
                 "def bravo(foo<caret>):\n" +
                 "    pass",
                 ":",
                 "def alpha(foo:):\n" +
                 "    pass\n" +
                 "\n" +
                 "def bravo(foo:):\n" +
                 "    pass");
  }

  public void testOverTypingColon() {
    doTestTyping("def func()<caret>: pass",
                 ":",
                 "def func():<caret> pass");
  }

  public void testOverTypingColonInStringLiteral() {
    doTestTyping("s = 'def func()<caret>: pass'",
                 ":",
                 "s = 'def func():<caret>: pass'");
  }

  public void testOverTypingColonInFStringFragment() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTestTyping("s = f'{(lambda<caret>: 42)}'",
                   ":",
                   "s = f'{(lambda:<caret> 42)}'");
    });
  }

  public void testOverTypingFormatStartInFStringFragment() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTestTyping("s = f'{42<caret>:3d}'",
                   ":",
                   "s = f'{42:<caret>:3d}'");
    });
  }

  @NotNull
  private PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }
}
