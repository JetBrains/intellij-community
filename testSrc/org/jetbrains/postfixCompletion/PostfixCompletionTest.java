package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// todo: check everything in field initializer!

public class PostfixCompletionTest extends LightCodeInsightFixtureTestCase {
  @Override protected String getTestDataPath() {
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/completion";
  }

  private void doTest(@NotNull String typingChars) { doTest(typingChars, false); }
  private void doTestForce(@NotNull String typingChars) { doTest(typingChars, true); }
  private void doTest(@NotNull String typingChars, boolean useBasic) {
    String name = getTestName(true);

    Pattern pattern = Pattern.compile("^(\\w+?)\\d+$");
    Matcher matcher = pattern.matcher(name);
    if (matcher.find()) {
      name = matcher.group(1).toLowerCase() + "/" + name;
    }

    myFixture.configureByFile(name + ".java");
    PostfixCompletionContributor.behaveAsAutoPopupForTests = !useBasic;
    myFixture.complete(CompletionType.BASIC);
    PostfixCompletionContributor.behaveAsAutoPopupForTests = false;

    for (int index = 0; index < typingChars.length(); index++) {
      myFixture.type(typingChars.charAt(index));
    }

    myFixture.checkResultByFile(name + "-out.java");
  }

  public void testArg01() { doTest("arg\n"); }

  public void testAssert01() { doTest("assert\n"); }

  public void testIf01() { doTest("if\n"); }
  public void testIf02() { doTest(""); }
  public void testIf03() { doTest("if\n"); }
  public void testIf04() { doTest("if\n"); }
  public void testIf05() { doTest("if\n"); }
  public void testIf06() { doTest("if\n"); }
  public void testIf07() { doTest("if\n"); }

  public void testElse01() { doTest("else\n"); }
  public void testElse02() { doTest("else\n"); }
  public void testElse03() { doTest("else\n"); }
  public void testElse04() { doTest("else\n"); }
  public void testElse05() { doTest("else\n"); }
  public void testElse06() { doTest("lse\n"); }

  public void testFori01() { doTest("fori\n"); }
  public void testFori02() { doTest("fori\n"); }
  public void testFori03() { doTest("fori\n"); }
  public void testFori04() { doTest("fori\n"); }
  public void testFori05() { doTest("fori\n"); }
  public void testFori06() { doTest("fori\n"); }
  public void testFori07() { doTest("fori\n"); }
  public void testFori08() { doTest("ori\n"); }

  public void testForr01() { doTest("forr\n"); }
  public void testForr02() { doTest("forr\n"); }
  public void testForr03() { doTest("forr\n"); }
  public void testForr04() { doTest("forr\n"); }
  public void testForr05() { doTest("forr\n"); }
  public void testForr06() { doTest("forr\n"); }
  public void testForr07() { doTest("forr\n"); }
  public void testForr08() { doTest("orr\n"); }

  public void testVar01() { doTest("var\n"); }
  public void testVar02() { doTest(""); }
  public void testVar03() { doTest("var\n"); }
  public void testVar04() { doTest("var\n"); }
  public void testVar05() { doTest("var\n"); }
  public void testVar06() { doTest("var\n"); }
  public void testVar07() { doTestForce("var\n"); }
  public void testVar08() { doTest("var\n"); }
  public void testVar09() { doTestForce("var\n"); }
  public void testVar10() { doTest("var\n"); }
  public void testVar11() { doTestForce("var\n"); }
  public void testVar12() { doTestForce("var\n"); }
  public void testVar13() { doTestForce("ar\n"); }
  public void testVar14() { doTest("var\n"); }
  public void testVar15() { doTestForce("var\n"); }
  public void testVar16() { doTestForce("var\n"); }
  public void testVar17() { doTestForce("var\n"); }

  public void testNotNull01() { doTest("nn\n"); }
  public void testNotNull02() { doTest("nn\n"); }
  public void testNull01() { doTest("null\n"); }

  public void testNot01() { doTest("not\n"); }
  public void testNot02() { doTest("not\n"); }
  public void testNot03() { doTest("not\n"); }

  public void testNew01() { doTest("new\n"); }
  public void testNew02() { doTest("new\n"); }
  public void testNew03() { doTest("new\n"); }
  public void testNew04() { doTest("new\n"); }
  public void testNew05() { doTest("new\n"); }
  public void testNew06() { doTest("new\n"); }
  public void testNew07() { doTest("new\n"); }
  public void testNew08() { doTestForce("new\n"); }

  public void testThrow01() { doTestForce("throw\n"); }
  public void testThrow02() { doTestForce("throw\n"); }
  public void testThrow03() { doTestForce("throw\n"); }
  public void testThrow04() { doTestForce("throw\n"); }

  public void testWhile01() { doTest("while\n"); }

  public void testCast01() { doTestForce("cast\n"); } // jdk mock required? 

  public void testPar01() { doTestForce("par\n"); }

  public void testReturn01() { doTest("return\n"); }
  public void testReturn02() { doTest("return\n"); }
  public void testReturn03() { doTest("return\n"); }
  public void testReturn04() { doTest("return\n"); }

  public void testSwitch01() { doTest("switch\n"); }
  public void testSwitch02() { doTest("switch\n"); }
  public void testSwitch03() { doTest("switch\n"); }
  public void testSwitch04() { doTest("switch\n"); }
  public void testSwitch05() { doTest("switch\n"); }
  public void testSwitch06() { doTest("switch\n"); }
  public void testSwitch07() { doTest("switch\n"); }

  public void testSynchronized01() { doTestForce("synchronized\n"); }

  public void testField01() { doTest("field\n"); }
  public void testField02() { doTestForce("field\n"); }

  public void testNoVariants01() { doTest(""); }
  public void testNoVariants02() { doTest("nn\n"); }
  public void testNoVariants03() { doTest("v\n"); }
  public void testNoVariants04() { doTest("nul\n"); }
  public void testNoVariants05() { doTest("var\n"); }
  public void testNoVariants06() { doTest("var\n"); }

  public void testInstanceof01() { doTest("instanceof\t"); } // caret position tbd
}

