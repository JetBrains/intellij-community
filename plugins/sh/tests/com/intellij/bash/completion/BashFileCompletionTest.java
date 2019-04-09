package com.intellij.bash.completion;

import com.intellij.bash.BashStringUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assume.assumeFalse;

public class BashFileCompletionTest extends LightCodeInsightFixtureTestCase {
  private static final String FOLDER_NAME = "example";
  private static final String FIRST_FILE_NAME = "simple1.txt";
  private static final String SECOND_FILE_NAME = "simple2.txt";
  private File myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    assumeFalse("Tests shouldn't run on Windows OS", SystemInfo.isWindows);
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
    assert new File(myTempDirectory, FIRST_FILE_NAME).createNewFile();
    assert new File(myTempDirectory, SECOND_FILE_NAME).createNewFile();

    String path = myTempDirectory.getCanonicalPath();
    myFixture.configureByText("a.sh", path + "/<caret>");
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, FIRST_FILE_NAME, SECOND_FILE_NAME);
  }

  public void testFolderCompletion() throws Exception {
    File folder = new File(myTempDirectory, FOLDER_NAME);
    assert folder.mkdir();
    assert new File(folder, FIRST_FILE_NAME).createNewFile();
    assert new File(folder, SECOND_FILE_NAME).createNewFile();

    String path = myTempDirectory.getCanonicalPath();
    myFixture.configureByText("a.sh", path + "/<caret>");
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, FOLDER_NAME);

    myFixture.type(Lookup.NORMAL_SELECT_CHAR);
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, FIRST_FILE_NAME, SECOND_FILE_NAME);
  }

  public void testFileNameEncoding() throws Exception {
    String folderName = " '\"#$=,[]!<>|;{}()*?^&`";
    assert new File(myTempDirectory, folderName).mkdir();

    String path = myTempDirectory.getCanonicalPath();
    myFixture.configureByText("a.sh", path + "/<caret>");
    String quotedName = BashStringUtil.quote(folderName);
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, quotedName);

    myFixture.type(Lookup.NORMAL_SELECT_CHAR);
    assertEquals(path + "/" + quotedName + "/", myFixture.getFile().getText());
  }

  public void testHomeDirCompletion() {
    myFixture.configureByText("a.sh", "~/<caret>");

    LookupElement[] lookupElements = myFixture.completeBasic();
    assertNotNull(lookupElements);
  }

  public void testCompletionWithPrefixMatch() throws IOException {
    assert new File(myTempDirectory, FIRST_FILE_NAME).createNewFile();
    assert new File(myTempDirectory, SECOND_FILE_NAME).createNewFile();

    String path = myTempDirectory.getCanonicalPath();
    myFixture.configureByText("a.sh", path + "/<caret>");
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, FIRST_FILE_NAME, SECOND_FILE_NAME);

    myFixture.type(Lookup.NORMAL_SELECT_CHAR);
    assertNull(myFixture.completeBasic());

    //To check prefix match caret should be here simple<caret>1.txt
    CaretModel caret = myFixture.getEditor().getCaretModel();
    caret.moveToOffset(caret.getOffset() - FileUtilRt.getExtension(FIRST_FILE_NAME).length() - 2);

    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, FIRST_FILE_NAME, SECOND_FILE_NAME);
  }

  public void testReplacement() throws Exception {
    assert new File(myTempDirectory, FOLDER_NAME).mkdir();
    assert new File(myTempDirectory, FIRST_FILE_NAME).createNewFile();
    assert new File(myTempDirectory, SECOND_FILE_NAME).createNewFile();

    String path = myTempDirectory.getCanonicalPath();
    myFixture.configureByText("a.sh", path + "/<caret>");
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, FOLDER_NAME, FIRST_FILE_NAME, SECOND_FILE_NAME);

    myFixture.type(Lookup.NORMAL_SELECT_CHAR);
    CaretModel caret = myFixture.getEditor().getCaretModel();
    caret.moveToOffset(caret.getOffset() - FOLDER_NAME.length() - 1);

    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, FOLDER_NAME, FIRST_FILE_NAME, SECOND_FILE_NAME);

    myFixture.type(FIRST_FILE_NAME.charAt(0));
    myFixture.type(Lookup.REPLACE_SELECT_CHAR);
    assertEquals(path + "/" + FIRST_FILE_NAME + "/", myFixture.getFile().getText());
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
