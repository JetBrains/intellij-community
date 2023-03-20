// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;


public class PyIndentTest extends PyTestCase {
  private void doTest(@NotNull String before, @NotNull String after) {
    myFixture.configureByText(getTestName(false) + ".py", before);
    pressButton(IdeActions.ACTION_EDITOR_ENTER);
    myFixture.checkResult(after);
  }

  public void testSimpleIndent() {
    doTest("a=1<caret>", "a=1\n<caret>");
  }

  public void testIndentColon() {
    doTest("if a:<caret>", "if a:\n    <caret>");
  }

  public void testIndentStatementList() {
    doTest("if a:<caret>\n    print a", "if a:\n    <caret>\n    print a");
  }

  public void testIndentStatementList2() {
    doTest("while a:\n    print a<caret>", "while a:\n    print a\n    <caret>");
  }

  public void testIndentStatementList3() {
    doTest("if a:\n    print a<caret>\n\nprint b", "if a:\n    print a\n    <caret>\n\nprint b");
  }

  public void testIndentOneLineStatementList() {
    doTest("if a:\n    if b: print c<caret>\n    print d", "if a:\n    if b: print c\n    <caret>\n    print d");
  }

  public void testIndentOneLineStatementListBreak() {
    doTest("if a:\n    if b:<caret> print c\n    print d", "if a:\n    if b:\n        <caret>print c\n    print d");
  }

  public void testAlignInList() {
    doTest("__all__ = [a,<caret>", "__all__ = [a,\n" + "           <caret>");
  }

  public void testAlignInListMiddle() {
    doTest("__all__ = [a,<caret>\n" +
           "           c]",
           """
             __all__ = [a,
                        <caret>
                        c]""");
  }

  public void testAlignInListMiddle2() {
    doTest("""
             __all__ = [a,
                        b,<caret>
                        c]""",
           """
             __all__ = [a,
                        b,
                        <caret>
                        c]""");
  }

  public void testAlignInListComp() {
    doTest("__all__ = [a for<caret>", "__all__ = [a for\n" + "           <caret>");
  }

  public void testAlignInListOnceMore() {  // PY-2407
    doTest("""
             for id in ["SEARCH_RESULT_ATTRIBUTES",\s
                        "WRITE_SEARCH_RESULT_ATTRIBUTES",\s
                        "IDENTIFIER_UNDER_CARET_ATTRIBUTES",<caret>]:""",
           """
             for id in ["SEARCH_RESULT_ATTRIBUTES",\s
                        "WRITE_SEARCH_RESULT_ATTRIBUTES",\s
                        "IDENTIFIER_UNDER_CARET_ATTRIBUTES",
                        <caret>]:""");
  }

  public void testAlignInDict() {
    doTest("some_call({'aaa': 'v1',<caret>})",
           "some_call({'aaa': 'v1',\n" +
           "           <caret>})");
  }

  public void testAlignInDictInParams() {  // PY-1947
    doTest("foobar({<caret>})",
           """
             foobar({
                 <caret>
             })""");
  }

  public void testIndentDictMissingValue() {  // PY-1469
    doTest("""
             some_dict = {
                 'key': <caret>
             }""",
           """
             some_dict = {
                 'key':\s
                     <caret>
             }""");
  }

  public void testIndentDictStringValue() {  // PY-1469
    doTest("""
             some_dict = {
                 'key': <caret>''
             }""",
           """
             some_dict = {
                 'key':\s
                     <caret>''
             }""");
  }

  public void testClass() {
    doTest("class A:\n" + "    print a<caret>", """
      class A:
          print a
          <caret>""");
  }

  public void testClass2() {
    doTest("""
             class CombatExpertiseFeat(Ability):
                 if a: print b
                 def getAvailableActions(self):<caret>""",
           """
             class CombatExpertiseFeat(Ability):
                 if a: print b
                 def getAvailableActions(self):
                     <caret>""");
  }

  public void testClass2_1() {
    doTest(
      """
        class CombatExpertiseFeat(Ability):
            if a: print b
            def getAvailableActions(self):<caret>
        class C2: pass""",

      """
        class CombatExpertiseFeat(Ability):
            if a: print b
            def getAvailableActions(self):
                <caret>
        class C2: pass""");
  }

  public void testMultiDedent() {
    doTest("""
             class CombatExpertiseFeat(Ability):
                 def getAvailableActions(self):
                     result = ArrayList()<caret>""",
           """
             class CombatExpertiseFeat(Ability):
                 def getAvailableActions(self):
                     result = ArrayList()
                     <caret>""");
  }

  public void testMultiDedent1() {
    doTest("""
             class CombatExpertiseFeat(Ability):
                 def getAvailableActions(self):
                     if a:<caret>""",
           """
             class CombatExpertiseFeat(Ability):
                 def getAvailableActions(self):
                     if a:
                         <caret>""");
  }

  public void testMultiDedent2() {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self): result = ArrayList()<caret>",
           """
             class CombatExpertiseFeat(Ability):
                 def getAvailableActions(self): result = ArrayList()
                 <caret>""");
  }

  public void testIfElse() {
    doTest("""
             if a:<caret>
                 b
             else:
                 c""", """
             if a:
                 <caret>
                 b
             else:
                 c""");
  }

  public void testIfElse2() {
    doTest("""
             if a:
                 b
             else:<caret>
                 c""", """
             if a:
                 b
             else:
                 <caret>
                 c""");
  }

  public void testEnterInEmptyParens() {      // PY-433
    doTest("foo(<caret>)", "foo(\n    <caret>\n)");
  }

  public void testEnterInEmptyList() {
    doTest("[<caret>]", "[\n    <caret>\n]");
  }

  public void testEnterInEmptyDict() {
    doTest("{<caret>}", "{\n    <caret>\n}");
  }

  public void testEnterInEmptyTuple() {
    doTest("(<caret>)", "(\n    <caret>\n)");
  }

  public void testEnterInNonEmptyTuple() {
    doTest("""
             (
                 'foo',<caret>
             )""",
           """
             (
                 'foo',
                 <caret>
             )""");
  }

  public void testEnterInNonEmptyArgList() {  // PY-1947
    doTest("Task(<caret>params=1)",
           "Task(\n" +
           "    <caret>params=1)");
  }

  public void testEnterInNonClosedArgList() {   // PY-4863
    doTest("""
             class C:
                 def new_method(self):
                     variable = self._stats.get('outer_key', 'inner_key',<caret>""",
           """
             class C:
                 def new_method(self):
                     variable = self._stats.get('outer_key', 'inner_key',
                                                <caret>""");
  }

  public void testEnterInSet() {  // PY-1947
    doTest("test_set = {<caret>'some_value'}",
           "test_set = {\n" +
           "    <caret>'some_value'}");

  }

  public void testEnterInList() {  // PY-1947
    doTest("test_list = [<caret>'some_value']",
           "test_list = [\n" +
           "    <caret>'some_value']");
  }

  public void testEnterInDictInTuple() {  // PY-1947
    doTest("td = ({<caret>})",
           """
             td = ({
                 <caret>
             })""");
  }

  public void testEnterInTuple() {  // PY-1947
    doTest("test_tuple = (<caret>'some_value')",
           "test_tuple = (\n" +
           "    <caret>'some_value')");
  }

  public void testIndentAfterComment() {   // PY-641
    doTest("def foo():\n    #some_call()<caret>\n    another_call()", "def foo():\n    #some_call()\n    <caret>\n    another_call()");
  }

  public void testUnindentAfterReturn() {  // PY-289
    doTest("""
             def foo():
                 if True:
                     return<caret>""",
           """
             def foo():
                 if True:
                     return
                 <caret>""");
  }

  public void testUnindentAfterReturnNotLast() {  // PY-289
    doTest("""
             def foo():
                 if True:
                     return<caret>
             def bar(): pass""",
           """
             def foo():
                 if True:
                     return
                 <caret>
             def bar(): pass"""
    );
  }

  public void testIndentAfterTrailingComment() {  // PY-2137
    doTest("""
             def foo()
                 a = 1
                 #comment<caret>
             """,
           """
             def foo()
                 a = 1
                 #comment
                 <caret>
             """);
  }

  public void testIndentAfterModuleLevelStatement() {  // PY-3572
    doTest("""
             def a(var):
                 if var == 'default':
                     pass

             b = 1<caret>
             """,
           """
             def a(var):
                 if var == 'default':
                     pass

             b = 1
             <caret>
             """);
  }

  public void testNestedLists() {  // PY-4034
    doTest("""
             mat = [
                 [1, 2, 3, 4, 4.5],
                 [5, 6, 7, 8],
                 [9, 10, 11, 12],<caret>
             ]""",
           """
             mat = [
                 [1, 2, 3, 4, 4.5],
                 [5, 6, 7, 8],
                 [9, 10, 11, 12],
                 <caret>
             ]""");
  }

  public void testAlignInCall() {  // PY-6360
    CodeStyle.getSettings(myFixture.getProject())
             .getCommonSettings(PythonLanguage.getInstance()).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest("list(a,<caret>)",
           "list(a,\n" +
           "     <caret>)");
  }

  public void testRespectDedent() {  // PY-3009
    doTest("""
             if True:
                 bar
             <caret>""",
           """
             if True:
                 bar

             <caret>""");
  }

  public void testIndentOnBackslash() {  // PY-7360
    doTest("def index():\n" +
           "    return 'some string' + \\<caret>",
           """
             def index():
                 return 'some string' + \\
                     <caret>""");
  }

  public void testIndentOnBackslash2() {  // PY-6359
    doTest("a = b\\<caret>",
           "a = b\\\n    <caret>");
  }

  public void testAlignListBeforeEquals() {
      doTest("__all__ <caret>= [a,\n" +
             "           b]",
             """
               __all__ \\
                   <caret>= [a,
                          b]""");
  }

  public void testAlignInIncompleteCall() {  // PY-6360
    doTest("""
             q = query.Nested(query.Term("type", "class"),<caret>

             def bar():
                 print('hello')""",
           """
             q = query.Nested(query.Term("type", "class"),
                              <caret>

             def bar():
                 print('hello')""");
  }

  // PY-24432
  public void testUnindentAfterEllipsis() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () ->
        doTest("""
                 def foo():
                     if True:
                         ...<caret>""",
               """
                 def foo():
                     if True:
                         ...
                     <caret>""")
    );
  }
}
