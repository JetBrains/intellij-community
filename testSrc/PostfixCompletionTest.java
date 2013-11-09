import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.*;

public class PostfixCompletionTest extends LightCodeInsightFixtureTestCase {
  @Override protected String getTestDataPath() {
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/completion";
  }

  private void test(@NotNull final String typingChars) {
    final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    final String name = trace[2].getMethodName();

    myFixture.testCompletionTyping(name + ".java", typingChars, name + "-out.java");
  }

  public void testIf01() { test("if\n"); }
  public void testIf02() { test(""); }
  public void testIf03() { test("if\n"); }
  public void testIf04() { test("if\n"); }

  public void testElse01() { test("else\n"); }
  public void testElse02() { test("else\n"); }

  public void testVar01() { test("var\n"); }
  public void testVar02() { test(""); }
  public void testVar03() { test("var\n"); }
  public void testVar04() { test("var\n"); }
  public void testVar05() { test("var\n"); }
  public void testVar06() { test("var\n"); }
  public void testVar07() { test("var\n"); }
  public void testVar08() { test("var\n"); }
  public void testVar09() { test("var\n"); }
}

