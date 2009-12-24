package com.jetbrains.python;

import com.intellij.idea.Bombed;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import java.io.IOException;
import java.util.Calendar;

/**
 * @author yole
 */
public class PyIndentTest extends PyLightFixtureTestCase {
  private void doTest(final String before, String after) throws Exception {
    final String name = getTestName(false);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          myFixture.configureByText(name + ".py", before);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      public void run() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
      }
    }, "", null);
    String s = myFixture.getFile().getText();
    myFixture.checkResult(after);
  }

  public void testSimpleIndent() throws Exception {
    doTest("a=1<caret>", "a=1\n<caret>");
  }

  public void testIndentColon() throws Exception {
    doTest("if a:<caret>", "if a:\n    <caret>");
  }

  public void testIndentStatementList() throws Exception {
    doTest("if a:<caret>\n    print a", "if a:\n    <caret>\n    print a");
  }

  public void testIndentStatementList2() throws Exception {
    doTest("while a:\n    print a<caret>", "while a:\n    print a\n    <caret>");
  }

  public void testIndentStatementList3() throws Exception {
    doTest("if a:\n    print a<caret>\n\nprint b", "if a:\n    print a\n    <caret>\n\nprint b");
  }

  public void testIndentOneLineStatementList() throws Exception {
    doTest("if a:\n    if b: print c<caret>\n    print d", "if a:\n    if b: print c\n    <caret>\n    print d");
  }

  public void testIndentOneLineStatementListBreak() throws Exception {
    doTest("if a:\n    if b:<caret> print c\n    print d", "if a:\n    if b:\n        <caret>print c\n    print d");
  }

  @Bombed(month = Calendar.FEBRUARY, day = 17, user = "yole")
  public void testAlignInList() throws Exception {
    doTest("__all__ = [a,<caret>", "__all__ = [a,\n" + "           <caret>");
  }

  public void testAlignInListMiddle() throws Exception {
    doTest("__all__ = [a,<caret>\n" + "           c]", "__all__ = [a,\n" + "           <caret>\n" + "           c]");
  }

  public void testAlignInListMiddle2() throws Exception {
    doTest("__all__ = [a,\n" + "           b,<caret>\n" + "           c]",
           "__all__ = [a,\n" + "           b,\n" + "           <caret>\n" + "           c]");
  }

  @Bombed(month = Calendar.FEBRUARY, day = 17, user = "yole")
  public void testAlignInListComp() throws Exception {
    doTest("__all__ = [a for<caret>", "__all__ = [a for\n" + "           <caret>");
  }

  public void testClass() throws Exception {
    doTest("class A:\n" + "    print a<caret>", "class A:\n" + "    print a\n" + "    <caret>");
  }

  public void testClass2() throws Exception {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    if a: print b\n" + "    def getAvailableActions(self):<caret>",
           "class CombatExpertiseFeat(Ability):\n" + "    if a: print b\n" + "    def getAvailableActions(self):\n" + "        <caret>");
  }

  public void testClass2_1() throws Exception {
    doTest(
      "class CombatExpertiseFeat(Ability):\n" + "    if a: print b\n" + "    def getAvailableActions(self):<caret>\n" + "class C2: pass",

      "class CombatExpertiseFeat(Ability):\n" +
      "    if a: print b\n" +
      "    def getAvailableActions(self):\n" +
      "        <caret>\n" +
      "class C2: pass");
  }

  public void testMultiDedent() throws Exception {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self):\n" + "        result = ArrayList()<caret>",
           "class CombatExpertiseFeat(Ability):\n" +
           "    def getAvailableActions(self):\n" +
           "        result = ArrayList()\n" +
           "        <caret>");
  }

  public void testMultiDedent1() throws Exception {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self):\n" + "        if a:<caret>",
           "class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self):\n" + "        if a:\n" + "            <caret>");
  }

  public void testMultiDedent2() throws Exception {
    doTest("class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self): result = ArrayList()<caret>",
           "class CombatExpertiseFeat(Ability):\n" + "    def getAvailableActions(self): result = ArrayList()\n" + "    <caret>");
  }

  public void testIfElse() throws Exception {
    doTest("if a:<caret>\n" + "    b\n" + "else:\n" + "    c", "if a:\n" + "    <caret>\n" + "    b\n" + "else:\n" + "    c");
  }

  public void testIfElse2() throws Exception {
    doTest("if a:\n" + "    b\n" + "else:<caret>\n" + "    c", "if a:\n" + "    b\n" + "else:\n" + "    <caret>\n" + "    c");
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
