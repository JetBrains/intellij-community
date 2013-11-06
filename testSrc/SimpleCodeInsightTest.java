import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.Arrays;
import java.util.List;

public class SimpleCodeInsightTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    final String basePath = getBasePath();
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/" + basePath;
  }

  public void testCompletion() {
    myFixture.configureByFiles("foo.java");
    myFixture.complete(CompletionType.BASIC, 1);
    List<String> strings = myFixture.getLookupElementStrings();

    assertTrue(strings.containsAll(Arrays.asList("key\\ with\\ spaces", "language", "message", "tab", "website")));
    assertEquals(5, strings.size());
  }
}

