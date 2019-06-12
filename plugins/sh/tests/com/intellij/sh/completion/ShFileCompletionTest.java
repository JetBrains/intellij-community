// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.sh.ShStringUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.Assume.assumeNotNull;

public class ShFileCompletionTest extends LightCodeInsightFixtureTestCase {
  private static final String FOLDER_NAME = "example";
  private static final String FIRST_FILE_NAME = "simple1.txt";
  private static final String SECOND_FILE_NAME = "simple2.txt";
  private File myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempDirectory = FileUtil.createTempDirectory(ShFileCompletionTest.class.getSimpleName().toLowerCase(Locale.ENGLISH), null, true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileUtil.delete(myTempDirectory);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSimple() throws Exception {
    if (SystemInfo.isWindows) return; // "Tests shouldn't run on Windows OS",
    assert new File(myTempDirectory, FIRST_FILE_NAME).createNewFile();
    assert new File(myTempDirectory, SECOND_FILE_NAME).createNewFile();

    String path = myTempDirectory.getCanonicalPath();
    myFixture.configureByText("a.sh", path + "/<caret>");
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, FIRST_FILE_NAME, SECOND_FILE_NAME);
  }

  public void testFolderCompletion() throws Exception {
    if (SystemInfo.isWindows) return; // "Tests shouldn't run on Windows OS",
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
    if (SystemInfo.isWindows) return; // "Tests shouldn't run on Windows OS",
    String folderName = " '\"#$=,[]!<>|;{}()*?^&`";
    assert new File(myTempDirectory, folderName).mkdir();

    String path = myTempDirectory.getCanonicalPath();
    myFixture.configureByText("a.sh", path + "/<caret>");
    String quotedName = ShStringUtil.quote(folderName);
    doTestVariantsInner(CompletionType.BASIC, 1, CheckType.EQUALS, quotedName);

    myFixture.type(Lookup.NORMAL_SELECT_CHAR);
    assertEquals(path + "/" + quotedName + "/", myFixture.getFile().getText());
  }

  public void testHomeDirCompletion() {
    if (SystemInfo.isWindows) return; // "Tests shouldn't run on Windows OS",
    myFixture.configureByText("a.sh", "~/<caret>");

    LookupElement[] lookupElements = myFixture.completeBasic();
    assertNotNull(lookupElements);
  }

  public void testEnvVariablesCompletion() {
    if (SystemInfo.isWindows) return; // "Tests shouldn't run on Windows OS",
    String envVar = "HOME";
    assumeNotNull(System.getenv(envVar));

    myFixture.configureByText("a.sh", "/<caret>");
    LookupElement[] rootLookupElements = myFixture.completeBasic();

    myFixture.configureByText("a.sh", "\"Text example $" + envVar + "\"/<caret>");
    LookupElement[] quotedWithWhitespaceLookupElements = myFixture.completeBasic();
    assertSameElements(rootLookupElements, quotedWithWhitespaceLookupElements);

    myFixture.configureByText("a.sh", "\"$" + envVar + "\"/<caret>");
    LookupElement[] quotedEnvVarLookupElements = myFixture.completeBasic();

    myFixture.configureByText("a.sh", "$" + envVar + "/<caret>");
    LookupElement[] envVarLookupElements = myFixture.completeBasic();
    assertSameElements(quotedEnvVarLookupElements, envVarLookupElements);

    myFixture.configureByText("a.sh", "~/<caret>");
    LookupElement[] homeDirLookupElements = myFixture.completeBasic();

    assertSameElements(envVarLookupElements, homeDirLookupElements);
  }

  public void testCompletionWithPrefixMatch() throws IOException {
    if (SystemInfo.isWindows) return; // "Tests shouldn't run on Windows OS",
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
    if (SystemInfo.isWindows) return; // "Tests shouldn't run on Windows OS",
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
        : ContainerUtil.map(elements, ShFileCompletionTest::getLookupString);

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
