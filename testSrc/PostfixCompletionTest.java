import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.LookupItems.*;
import org.jetbrains.postfixCompletion.*;

// todo: test with statements after
// todo: dump caret position after completion

public class PostfixCompletionTest extends LightCodeInsightFixtureTestCase {
  @Override protected String getTestDataPath() {
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/completion";
  }

  // todo: force mode flag
  private void test(@NotNull String typingChars) {
    test(typingChars, false);
  }

  private void testForce(@NotNull String typingChars) {
    test(typingChars, true);
  }

  private void test(@NotNull String typingChars, boolean useBasic) {
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    String name = trace[3].getMethodName();

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
        editor.getDocument().insertString(0, dumpItems(autoItems));
      }
    });

    myFixture.checkResultByFile(name + "-out.java");
  }

  @NotNull private String dumpItems(@Nullable LookupElement[] elements) {
    StringBuilder builder = new StringBuilder("// Items: ");

    if (elements != null && elements.length > 0) {
      boolean first = true;
      for (LookupElement item : elements) {
        if (item instanceof PostfixLookupElement) {
          if (first) first = false; else builder.append(", ");
          builder.append(item.getLookupString());
        }
      }
    } else builder.append("<no items>");

    builder.append(SystemProperties.getLineSeparator());
    return builder.toString();
  }

  public void testIf01() { test("if\n"); }
  public void testIf02() { test(""); }
  public void testIf03() { test("if\n"); }
  public void testIf04() { test("if\n"); }
  public void testIf05() { test("if\n"); }
  public void testIf06() { test("if\n"); }

  public void testElse01() { test("else\n"); }
  public void testElse02() { test("else\n"); }
  public void testElse03() { test("else\n"); }
  public void testElse04() { test("else\n"); }
  public void testElse05() { test("else\n"); }

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

  public void testNotNull01() { test("nn\n"); }
  public void testNull01() { test("null\n"); }

  public void testNot01() { test("not\n"); }
  public void testNot02() { test("not\n"); }
}

