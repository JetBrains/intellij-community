import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.*;

import java.util.regex.*;

// todo: check everything in field initializer!

public class PostfixCompletionTest extends LightCodeInsightFixtureTestCase {
  @Override protected String getTestDataPath() {
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/completion";
  }

  private void test(@NotNull String typingChars) {
    test(typingChars, false);
  }

  private void testForce(@NotNull String typingChars) {
    test(typingChars, true);
  }

  private void test(@NotNull String typingChars, boolean useBasic) {
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    String name = trace[3].getMethodName();

    Pattern pattern = Pattern.compile("^test(\\w+?)\\d+$");
    Matcher matcher = pattern.matcher(name);
    if (matcher.find()) {
      name = matcher.group(1).toLowerCase() + "/" + name;
    }

    myFixture.configureByFile(name + ".java");

    PostfixCompletionContributor.behaveAsAutoPopupForTests = !useBasic;

    myFixture.complete(CompletionType.BASIC);

    final LookupElement[] autoItems = myFixture.getLookupElements();

    PostfixCompletionContributor.behaveAsAutoPopupForTests = false;

    // type item name
    for (int index = 0; index < typingChars.length(); index++) {
      myFixture.type(typingChars.charAt(index));
    }

    // dump caret position and available items
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override public void run() {
        Editor editor = myFixture.getEditor();
        editor.getDocument().insertString(0, PostfixTestUtils.dumpItems(autoItems));
      }
    });

    myFixture.checkResultByFile(name + "-out.java");
  }

  public void testArg01() { test("arg\n"); }

  public void testIf01() { test("if\n"); }
  public void testIf02() { test(""); }
  public void testIf03() { test("if\n"); }
  public void testIf04() { test("if\n"); }
  public void testIf05() { test("if\n"); }
  public void testIf06() { test("if\n"); }
  public void testIf07() { test("if\n"); }

  public void testElse01() { test("else\n"); }
  public void testElse02() { test("else\n"); }
  public void testElse03() { test("else\n"); }
  public void testElse04() { test("else\n"); }
  public void testElse05() { test("else\n"); }
  public void testElse06() { test("lse\n"); }

  public void testFori01() { test("fori\n"); }
  public void testFori02() { test("fori\n"); }
  public void testFori03() { test("fori\n"); }
  public void testFori04() { test("fori\n"); }
  public void testFori05() { test("fori\n"); }
  public void testFori06() { test("fori\n"); }
  public void testFori07() { test("fori\n"); }
  public void testFori08() { test("ori\n"); }

  public void testForr01() { test("forr\n"); }
  public void testForr02() { test("forr\n"); }
  public void testForr03() { test("forr\n"); }
  public void testForr04() { test("forr\n"); }
  public void testForr05() { test("forr\n"); }
  public void testForr06() { test("forr\n"); }
  public void testForr07() { test("forr\n"); }
  public void testForr08() { test("orr\n"); }

  public void testVar01() { test("var\n"); }
  public void testVar02() { test(""); }
  public void testVar03() { test("var\n"); }
  public void testVar04() { test("var\n"); }
  public void testVar05() { test("var\n"); }
  public void testVar06() { test("var\n"); }
  public void testVar07() { testForce("var\n"); }
  public void testVar08() { test("var\n"); }
  public void testVar09() { testForce("var\n"); }
  public void testVar10() { test("var\n"); }
  public void testVar11() { testForce("var\n"); }
  public void testVar12() { testForce("var\n"); }
  public void testVar13() { testForce("ar\n"); }
  public void testVar14() { test("var\n"); }
  public void testVar15() { testForce("var\n"); }
  public void testVar16() { testForce("var\n"); }
  public void testVar17() { testForce("var\n"); }

  public void testNotNull01() { test("nn\n"); }
  public void testNull01() { test("null\n"); }

  public void testNot01() { test("not\n"); }
  public void testNot02() { test("not\n"); }
  public void testNot03() { test("not\n"); }

  public void testNew01() { test("new\n"); }
  public void testNew02() { test("new\n"); }
  public void testNew03() { test("new\n"); }
  public void testNew04() { test("new\n"); }
  public void testNew05() { test("new\n"); }
  public void testNew06() { test("new\n"); }
  public void testNew07() { test("new\n"); }
  public void testNew08() { testForce("new\n"); }

  public void testThrow01() { testForce("throw\n"); }
  public void testThrow02() { testForce("throw\n"); }
  public void testThrow03() { testForce("throw\n"); }

  public void testWhile01() { test("while\n"); }

  public void testCast01() { testForce("cast\n"); }

  public void testPar01() { testForce("par\n"); }

  public void testReturn01() { test("return\n"); }
  public void testReturn02() { test("return\n"); }
  public void testReturn03() { test("return\n"); }
  public void testReturn04() { test("return\n"); }

  public void testSwitch01() { test("switch\n"); }
  public void testSwitch02() { test("switch\n"); }
  public void testSwitch03() { test("switch\n"); }
  public void testSwitch04() { test("switch\n"); }
  public void testSwitch05() { test("switch\n"); }
  public void testSwitch06() { test("switch\n"); }
  public void testSwitch07() { test("switch\n"); }

  public void testField01() { test("field\n"); }
  public void testField02() { testForce("field\n"); }

  public void testNoVariants01() { test(""); }
  public void testNoVariants02() { test("nn\n"); }
  public void testNoVariants03() { test("v\n"); }
  public void testNoVariants04() { test("nul\n"); }
  public void testNoVariants05() { test("var\n"); }
  public void testNoVariants06() { test("var\n"); }
}

