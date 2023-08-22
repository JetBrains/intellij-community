// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CustomDictionaryTest extends SpellcheckerInspectionTestCase {
  private static final String TEST_DIC = "test.dic";
  private static final String NEW_TEST_DIC = "new_" + TEST_DIC;
  private static final String TEST_DIC_AFTER = TEST_DIC + ".after";
  public static final String TEMP = "temp";
  private SpellCheckerSettings settings;
  private SpellCheckerManager spellCheckerManager;
  private VirtualFile dictDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    settings = SpellCheckerSettings.getInstance(getProject());
    spellCheckerManager = SpellCheckerManager.getInstance(getProject());

    List<String> oldPaths = settings.getCustomDictionariesPaths();
    WriteAction.runAndWait(() -> {
      dictDir = VfsUtil.createDirectoryIfMissing(getProject().getBasePath() + "/" + getDictDirName());
      VfsUtil.copyDirectory(this, myFixture.copyDirectoryToProject(getTestName(true), getDictDirName()), dictDir, null);
    });

    List<String> testDictionaries = new ArrayList<>();
    VfsUtilCore.processFilesRecursively(dictDir, file -> {
      if (FileUtilRt.extensionEquals(file.getPath(), "dic")) {
        testDictionaries.add(PathUtil.toSystemDependentName(file.getPath()));
      }
      return true;
    });
    settings.setCustomDictionariesPaths(testDictionaries);
    Disposer.register(getTestRootDisposable(), () -> settings.setCustomDictionariesPaths(oldPaths));
    spellCheckerManager.fullConfigurationReload();
  }

  @NotNull
  private String getDictDirName() {
    return getTestName(true) + "_dict";
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (dictDir.exists()) {
        WriteAction.run(() -> dictDir.delete(this));
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getBasePath() {
    return Paths.get(getSpellcheckerTestDataPath(), "inspection", "dictionary").toString();
  }

  private VirtualFile getTestDictionaryFile() {
    return dictDir.findChild(TEST_DIC);
  }

  private VirtualFile getTestDictionaryAfter() {
    return dictDir.findChild(TEST_DIC_AFTER);
  }

  private void modifyDictContent(String newContent) throws IOException {
    WriteAction.run(() -> VfsUtil.saveText(dictDir.findChild(TEST_DIC), newContent));
  }

  private void doBeforeCheck() {
    doTest(Paths.get(getTestName(true), "test.before.php").toString());
  }

  private void doAfterCheck() {
    doTest(Paths.get(getTestName(true), "test.after.php").toString());
  }

  private void doTest() throws IOException {
    doBeforeCheck();
    modifyDictContent(VfsUtilCore.loadText(getTestDictionaryAfter()));
    doAfterCheck();
  }

  private void doNewDictTest() throws IOException {
    doBeforeCheck();
    WriteAction.run(() -> getTestDictionaryAfter().copy(this, getTestDictionaryAfter().getParent(), NEW_TEST_DIC));
    doAfterCheck();
  }

  private void doLoadTest() throws IOException {
    final VirtualFile file = getTestDictionaryAfter();
    final String new_test_dic = FileUtil.toSystemIndependentName(file.getParent().getPath() + File.separator + NEW_TEST_DIC);
    settings.getCustomDictionariesPaths().add(new_test_dic);
    spellCheckerManager.fullConfigurationReload();
    try {
      doBeforeCheck();
      WriteAction.run(() -> file.copy(this, file.getParent(), NEW_TEST_DIC));
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> file.getParent().findChild(NEW_TEST_DIC).delete(this));
      settings.getCustomDictionariesPaths().remove(new_test_dic);
      spellCheckerManager.fullConfigurationReload();
    }
  }

  private void doRemoveDictTest() throws IOException {
    doBeforeCheck();
    WriteAction.run(() -> getTestDictionaryFile().delete(this));
    doAfterCheck();
  }

  public void testAddDictionary() throws IOException {
    doNewDictTest();
  }

  public void testAddOneMoreDictionary() throws IOException {
    doNewDictTest();
  }

  public void testRemoveDictionary() throws IOException {
    doRemoveDictTest();
  }

  public void testRemoveOneOfDictionaries() throws IOException {
    doRemoveDictTest();
  }

  public void testAddToCustomDic() throws IOException {
    doTest();
  }

  public void testAddAnotherToCustomDic() throws IOException {
    doTest();
  }

  public void testRemoveFromCustomDic() throws IOException {
    doTest();
  }

  public void testAddSeveralWords() throws IOException {
    doTest();
  }

  public void testModifyDict() throws IOException {
    doTest();
  }

  public void testUtf8Dict() throws IOException {
    doLoadTest();
  }

  public void testUtf16BEDict() throws IOException {
    doLoadTest();
  }

  public void testUtf16DictFirstWordToCheck() throws IOException {
    doLoadTest();
  }

  public void testUtf16LEDict() throws IOException {
    doLoadTest();
  }

  public void testMoveDict() throws IOException {
    try {
      doBeforeCheck();
      WriteAction.run(() -> getTestDictionaryFile().move(this, PlatformTestUtil.getOrCreateProjectBaseDir(getProject())));
      doAfterCheck();
    }
    finally {
      WriteAction.run(() -> {
        final VirtualFile child = PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).findChild(TEST_DIC);
        if (child.exists()) {
          child.delete(this);
        }
      });
    }
  }

  public void testRenameToDict() throws IOException {
    doBeforeCheck();
    WriteAction.run(() -> getTestDictionaryAfter().rename(this, TEST_DIC));
    doAfterCheck();
  }

  public void testRenameToTxt() throws IOException {
    doBeforeCheck();
    WriteAction.run(() -> getTestDictionaryFile().rename(this, "test.txt"));
    doAfterCheck();
  }

  public void testRenameStillDicExtension() throws IOException {
    doBeforeCheck();
    WriteAction.run(() -> getTestDictionaryFile().rename(this, "still.dic"));
    doAfterCheck();
  }

  public void testRenameStillNotDicExtension() throws IOException {
    doBeforeCheck();
    WriteAction.run(() -> getTestDictionaryAfter().rename(this, "still_not_dic.extension"));
    doAfterCheck();
  }


  public void testRemoveDictDir() throws IOException {
    doBeforeCheck();
    WriteAction.run(() -> dictDir.delete(this));
    doAfterCheck();
  }

  public void testMoveDictDir() throws IOException {
    try {
      doBeforeCheck();
      WriteAction.run(
        () -> dictDir.move(this, PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).createChildDirectory(this, "new_dir"))
      );
      doAfterCheck();
    }
    finally {
      WriteAction.run(() -> {
        final VirtualFile dir = PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).findChild("new_dir");
        if (dir.exists()) {
          dir.delete(this);
        }
      });
    }
  }
}
