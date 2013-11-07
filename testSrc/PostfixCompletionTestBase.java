import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class PostfixCompletionTestBase extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/completion";
  }

  private void test(@NotNull final String typingChars) {
    final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    final String methodName = trace[2].getMethodName();

    myFixture.testCompletionTyping(methodName + ".java", typingChars, methodName + ".gold");
  }

  public void testIf01() { test("if\n"); }
  public void testIf02() { test("f\n"); }
  public void testIf03() { test("if\n"); }
}

