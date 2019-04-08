package com.intellij.bash.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BashFileCompletionTest extends LightCodeInsightFixtureTestCase {
  private File myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempDirectory = FileUtil.createTempDirectory(BashFileCompletionTest.class.getSimpleName().toLowerCase(), null, true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileUtil.delete(myTempDirectory);
    } finally {
      super.tearDown();
    }
  }

  public void testSimple() throws Exception {
    if (SystemInfo.isWindows) return; // todo: use assume in setup, avoid copying

    assert new File(myTempDirectory, "simple1.txt").createNewFile();
    assert new File(myTempDirectory, "simple2.txt").createNewFile();

    String path = myTempDirectory.getCanonicalPath();
    myFixture.configureByText("a.sh", path + "/<caret>");
    myFixture.completeBasic();
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, "simple1.txt", "simple2.txt");
  }

  private void doTestVariantsInner(@NotNull CompletionType type, int count, CheckType checkType, String... variants) {
    myFixture.complete(type, count);
    LookupElement[] elements = myFixture.getLookupElements();
    List<String> stringList = elements == null
        ? null
        : ContainerUtil.map(elements, BashFileCompletionTest::getLookupString);

    assertNotNull("\nPossibly the single variant has been completed.\nFile after:\n" + myFixture
        .getFile().getText(), stringList);
    Collection<String> varList = ContainerUtil.newArrayList(variants);
    if (checkType == CheckType.ORDERED_EQUALS) {
      assertOrderedEquals(stringList, variants);
    }
    else if (checkType == CheckType.EQUALS) {
      assertSameElements(stringList, variants);
    }
    else if (checkType == CheckType.INCLUDES) {
      varList.removeAll(stringList);
      assertTrue("Missing variants: " + varList, varList.isEmpty());
    }
    else if (checkType == CheckType.EXCLUDES) {
      varList.retainAll(stringList);
      assertTrue("Unexpected variants: " + varList, varList.isEmpty());
    }
  }

  private enum CheckType {EQUALS, EXCLUDES, INCLUDES, ORDERED_EQUALS}

  private static String getLookupString(@NotNull LookupElement e) {
    return StringUtil.defaultIfEmpty(e.getLookupString(),
        e.getAllLookupStrings().stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(", ")));
  }
}
