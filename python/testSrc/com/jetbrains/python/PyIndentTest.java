package com.jetbrains.python;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.CommandProcessor;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyIndentTest extends PyLightFixtureTestCase {
  private void doTest(final String before, String after) {
    final String name = getTestName(false);

    myFixture.configureByText(name + ".py", before);
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
      }
    }, "", null);
    String s = myFixture.getFile().getText();
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
           "__all__ = [a,\n" +
           "           <caret>\n" +
           "           c]");
  }

  public void testAlignInListMiddle2() {
    doTest("__all__ = [a,\n" + "           b,<caret>\n" + "           c]",
           "__all__ = [a,\n" + "           b,\n" + "           <caret>\n" + "           c]");
  }

  public void testAlignInListComp() {
    doTest("__all__ = [a for<caret>", "__all__ = [a for\n" + "           <caret>");
  }

  public void testAlignInListOnceMore() {  // PY-2407
    doTest("for id in [\"SEARCH_RESULT_ATTRIBUTES\", \n" +
           "           \"WRITE_SEARCH_RESULT_ATTRIBUTES\", \n" +
           "           \"IDENTIFIER_UNDER_CARET_ATTRIBUTES\",<caret>]:",
           "for id in [\"SEARCH_RESULT_ATTRIBUTES\", \n" +
           "           \"WRITE_SEARCH_RESULT_ATTRIBUTES\", \n" +
           "           \"IDENTIFIER_UNDER_CARET_ATTRIBUTES\",\n" +
           "           <caret>]:");
  }

  public void testAlignInDict() {
    doTest("some_call({'aaa': 'v1',<caret>})",
           "some_call({'aaa': 'v1',\n" +
           "           <caret>})");
  }

  public void testAlignInDictInParams() {  // PY-1947
    doTest("foobar({<caret>})",
           "foobar({\n" +
           "    <caret>\n" +
           "})");
  }

  public void testIndentDictMissingValue() {  // PY-1469
    doTest("some_dict = {\n" +
           "    'key': <caret>\n" +
           "}",
           "some_dict = {\n" +
           "    'key': \n" +
           "        <caret>\n" +
           "}");
  }

  public void testIndentDictStringValue() {  // PY-1469
    doTest("some_dict = {\n" +
           "    'key': <caret>''\n" +
           "}",
           "some_dict = {\n" +
           "    'key': \n" +
           "        <caret>''\n" +
           "}");
  }

  public void testClass() {
    doTest("class A:\n" + "    print a<caret>", "class A:\n" + "    print a\n" + "    <caret>");
  }

  public void testClass2() {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    if a: print b\n" + "    def getAvailableActions(self):<caret>",
           "class CombatExpertiseFeat(Ability):\n" + "    if a: print b\n" + "    def getAvailableActions(self):\n" + "        <caret>");
  }

  public void testClass2_1() {
    doTest(
      "class CombatExpertiseFeat(Ability):\n" + "    if a: print b\n" + "    def getAvailableActions(self):<caret>\n" + "class C2: pass",

      "class CombatExpertiseFeat(Ability):\n" +
      "    if a: print b\n" +
      "    def getAvailableActions(self):\n" +
      "        <caret>\n" +
      "class C2: pass");
  }

  public void testMultiDedent() {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self):\n" + "        result = ArrayList()<caret>",
           "class CombatExpertiseFeat(Ability):\n" +
           "    def getAvailableActions(self):\n" +
           "        result = ArrayList()\n" +
           "        <caret>");
  }

  public void testMultiDedent1() {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self):\n" + "        if a:<caret>",
           "class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self):\n" + "        if a:\n" + "            <caret>");
  }

  public void testMultiDedent2() {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self): result = ArrayList()<caret>",
           "class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self): result = ArrayList()\n" + "    <caret>");
  }

  public void testIfElse() {
    doTest("if a:<caret>\n" + "    b\n" + "else:\n" + "    c", "if a:\n" + "    <caret>\n" + "    b\n" + "else:\n" + "    c");
  }

  public void testIfElse2() {
    doTest("if a:\n" + "    b\n" + "else:<caret>\n" + "    c", "if a:\n" + "    b\n" + "else:\n" + "    <caret>\n" + "    c");
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
    doTest("(\n" +
           "    'foo',<caret>\n" +
           ")",
           "(\n" +
           "    'foo',\n" +
           "    <caret>\n" +
           ")");
  }

  public void testEnterInNonEmptyArgList() {  // PY-1947
    doTest("Task(<caret>params=1)",
           "Task(\n" +
           "    <caret>params=1)");
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
           "td = ({\n" +
           "    <caret>\n" +
           "})");
  }

  public void testIndentAfterComment() {   // PY-641
    doTest("def foo():\n    #some_call()<caret>\n    another_call()", "def foo():\n    #some_call()\n    <caret>\n    another_call()");
  }

  public void testUnindentAfterReturn() {  // PY-289
    doTest("def foo():\n" +
           "    if True:\n" +
           "        return<caret>",
           "def foo():\n" +
           "    if True:\n" +
           "        return\n" +
           "    <caret>");
  }

  public void testUnindentAfterReturnNotLast() {  // PY-289
    doTest("def foo():\n" +
           "    if True:\n" +
           "        return<caret>\n" +
           "def bar(): pass",
           "def foo():\n" +
           "    if True:\n" +
           "        return\n" +
           "    <caret>\n" +
           "def bar(): pass"
    );
  }

  public void testIndentAfterTrailingComment() {  // PY-2137
    doTest("def foo()\n" +
           "    a = 1\n" +
           "    #comment<caret>\n",
           "def foo()\n" +
           "    a = 1\n" +
           "    #comment\n" +
           "    <caret>\n");
  }

  public void testIndentAfterModuleLevelStatement() {  // PY-3572
    doTest("def a(var):\n" +
           "    if var == 'default':\n" +
           "        pass\n" +
           "\n" +
           "b = 1<caret>\n" +
           "",
           "def a(var):\n" +
           "    if var == 'default':\n" +
           "        pass\n" +
           "\n" +
           "b = 1\n" +
           "<caret>\n" +
           "");
  }

  /*
  TODO: formatter core problem?
  public void testAlignListBeforeEquals() throws Exception {
      doTest("__all__ <caret>= [a,\n" +
             "           b]",
             "__all__ \n" +
             "<caret>= [a,\n" +
             "           b]");
  }
  */
}
