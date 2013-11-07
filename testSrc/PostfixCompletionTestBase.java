import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class PostfixCompletionTestBase extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/completion";
  }


  private void doTest(@NotNull final String name) {
    myFixture.configureByFile(name);
    myFixture.complete(CompletionType.BASIC);

    //myFixture.testCompletion();

  }

  public void testIf01() {
    myFixture.testCompletionTyping("testIf01.java", "if\n", "testIf01.gold");
  }

  public void testCompletion() {

    //myFixture.testCompletion();

    myFixture.configureByFiles("foo.java");
    myFixture.complete(CompletionType.BASIC, 1);
    List<String> strings = myFixture.getLookupElementStrings();



    assertTrue(strings.containsAll(Arrays.asList("key\\ with\\ spaces", "language", "message", "tab", "website")));
    assertEquals(5, strings.size());
  }
}

