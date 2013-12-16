package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// todo: check everything in field initializer!

public class PostfixCompletionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/completion";
  }

  private void doTest(@NotNull String typingChars) {
    doTest(typingChars, false);
  }

  private void doTestForce(@NotNull String typingChars) {
    doTest(typingChars, true);
  }

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

  public void testNoVariants01() {
    doTest("");
  }
}

