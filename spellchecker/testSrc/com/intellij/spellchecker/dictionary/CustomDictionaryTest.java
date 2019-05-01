// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.spellchecker.util.SPFileUtil;
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
  private static final String TEMP_DIC = TEST_DIC + ".temp";
  private static final String TEST_DIC_DIR = "testDir" ;
  public static final String TEMP = "temp";
  SpellCheckerSettings settings;
  SpellCheckerManager spellCheckerManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    settings = SpellCheckerSettings.getInstance(getProject());
    spellCheckerManager = SpellCheckerManager.getInstance(getProject());

    List<String> oldPaths = settings.getCustomDictionariesPaths();
    List<String> testDictionaries = new ArrayList<>();
    SPFileUtil.processFilesRecursively(getTestDictDirectory(), file -> {
      if(FileUtilRt.extensionEquals(file, "dic")){
        testDictionaries.add(file);
      }
    });
    settings.setCustomDictionariesPaths(testDictionaries);
    Disposer.register(getTestRootDisposable(), () -> settings.setCustomDictionariesPaths(oldPaths));
    spellCheckerManager.fullConfigurationReload();
  }

  @Override
  protected String getBasePath() {
    return Paths.get(getSpellcheckerTestDataPath(), "inspection", "dictionary").toString();
  }

  private String getTestDictionary() {
    return Paths.get(getTestDictDirectory(), TEST_DIC).toString();
  }

  private String getTestDictDirectory() {
    return Paths.get(myFixture.getTestDataPath(), getTestName(true)).toString();
  }

  private VirtualFile getTestDictionaryFile() {
    return VfsUtil.findFileByIoFile(Paths.get(getTestDictionary()).toFile(), true);
  }

  private String loadFromTestDictionary() throws IOException {
    final VirtualFile file = VfsUtil.findFileByIoFile(new File(getTestDictionary()), true);
    if (file == null) return null;
    return VfsUtilCore.loadText(file);
  }

  private void modifyDictContent(String newContent) throws IOException {
    WriteAction.run(() -> VfsUtil.saveText(getTestDictionaryFile(), newContent));
  }

  private void doBeforeCheck() {
    doTest(Paths.get(getTestName(true), "test.before.php").toString());
  }

  private void doAfterCheck() {
    doTest(Paths.get(getTestName(true), "test.after.php").toString());
  }

  private void doTest() throws IOException {
    final String oldDictContent = loadFromTestDictionary();
    try {
      doBeforeCheck();
      modifyDictContent(VfsUtilCore.loadText(VfsUtil.findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC_AFTER).toFile(), true)));
      doAfterCheck();
    }
    finally {
      //back to initial state
      modifyDictContent(oldDictContent);
    }
  }

  private void doNewDictTest() throws IOException {
    final VirtualFile file = VfsUtil.findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC_AFTER).toFile(), true);
    try {
      doBeforeCheck();
      WriteAction.run(() -> file.copy(this, file.getParent(), NEW_TEST_DIC));
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> file.getParent().findChild(NEW_TEST_DIC).delete(this));
    }
  }

  private void doLoadTest() throws IOException {
    final VirtualFile file = VfsUtil.findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC_AFTER).toFile(), true);
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
    try {
      doBeforeCheck();
      WriteAction.run(() -> {
        getTestDictionaryFile().copy(this, getTestDictionaryFile().getParent(), TEMP_DIC); // to revert it back
        getTestDictionaryFile().delete(this);
      });
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> VfsUtil.findFileByIoFile(Paths.get(getTestDictDirectory(), TEMP_DIC).toFile(), true).rename(this, TEST_DIC));
    }
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
    final VirtualFile tempDir = VfsUtil.findFileByIoFile(FileUtil.createTempDirectory(TEST_DIC_DIR, TEMP), true);
    final VirtualFile testDir = VfsUtil.findFileByIoFile(new File(getTestDictDirectory()), true);
    final VirtualFile file = testDir.findChild(TEST_DIC_AFTER);

    doBeforeCheck();

    WriteAction.run(() -> {
      final VirtualFile copy = file.copy(this, tempDir, TEST_DIC);
      copy.move(this, testDir);
    });

    try {
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> {
        tempDir.delete(this);
        testDir.findChild(TEST_DIC).delete(this);
      });
    }
  }

  public void testMoveDictOutside() throws IOException {
    VirtualFile tempDir = VfsUtil.findFileByIoFile(FileUtil.createTempDirectory(TEST_DIC_DIR, TEMP), true);
    VirtualFile testDir = VfsUtil.findFileByIoFile(new File(getTestDictDirectory()), true);
    moveFileToDirAndCheck(testDir.findChild(TEST_DIC), testDir, tempDir);
  }

  public void testMoveNotInDictFolder() throws IOException {
    final VirtualFile tempDir1 = VfsUtil.findFileByIoFile(FileUtil.createTempDirectory(TEST_DIC_DIR, TEMP + "1"), true);
    final VirtualFile tempDir2 = VfsUtil.findFileByIoFile(FileUtil.createTempDirectory(TEST_DIC_DIR, TEMP + "2"), true);

    final VirtualFile testDir = VfsUtil.findFileByIoFile(new File(getTestDictDirectory()), true);
    final VirtualFile file = testDir.findChild(TEST_DIC_AFTER);
    WriteAction.run(() -> file.copy(this, tempDir1, TEST_DIC));

    try {
      doBeforeCheck();
      WriteAction.run(() -> tempDir1.findChild(TEST_DIC).move(this, tempDir2));
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> {
        tempDir1.delete(this);
        tempDir2.delete(this);
      });
    }
  }

  public void testMoveInsideDictFolders() throws IOException {
    final VirtualFile testDir = VfsUtil.findFileByIoFile(new File(getTestDictDirectory()), true);
    final VirtualFile file = testDir.findChild(TEST_DIC);

    final String yetAnotherDirName = "yetAnotherDir";
    WriteAction.run(() -> testDir.createChildDirectory(this, yetAnotherDirName));
    final VirtualFile anotherDir = testDir.findChild(yetAnotherDirName);
    moveFileToDirAndCheck(file, testDir, anotherDir);
  }

  private void moveFileToDirAndCheck(@NotNull VirtualFile file, @NotNull VirtualFile from, @NotNull VirtualFile to) throws IOException {
    doBeforeCheck();
    WriteAction.run(() -> file.move(this, to));
    try {
      doAfterCheck();
    }
    finally {
      // back to initial state
      WriteAction.run(() -> {
        if (to.findChild(TEST_DIC) != null) {
          to.findChild(TEST_DIC).move(this, from);
        }
        to.delete(this);
      });
    }
  }

  public void testRenameToDict() throws IOException {
    final VirtualFile file = VfsUtil.findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC_AFTER).toFile(), true);
    try {
      doBeforeCheck();
      WriteAction.run(() -> file.rename(this, TEST_DIC));
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> file.rename(this, TEST_DIC_AFTER));
    }
  }

  public void testRenameToTxt() throws IOException {
    final VirtualFile file = VfsUtil.findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC).toFile(), true);
    try {
      doBeforeCheck();
      WriteAction.run(() -> file.rename(this, "test.txt"));
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> file.rename(this, TEST_DIC));
    }
  }

  public void testRenameStillDicExtension() throws IOException {
    final VirtualFile file = VfsUtil.findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC).toFile(), true);
    try {
      doBeforeCheck();
      WriteAction.run(() -> file.rename(this, "still.dic"));
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> file.rename(this, TEST_DIC));
    }
  }

  public void testRenameStillNotDicExtension() throws IOException {
    final VirtualFile file = VfsUtil.findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC_AFTER).toFile(), true);
    try {
      doBeforeCheck();
      WriteAction.run(() -> file.rename(this, "still_not_dic.extension"));
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> file.rename(this, TEST_DIC_AFTER));
    }
  }


  public void testRemoveDictDir() throws IOException {
    final VirtualFile tempDir = VfsUtil.findFileByIoFile(FileUtil.createTempDirectory(TEST_DIC_DIR, TEMP), true);
    final VirtualFile testDir = VfsUtil.findFileByIoFile(new File(getTestDictDirectory()), true);
    final VirtualFile testDictDir = testDir.findChild(TEST_DIC_DIR);
    try {
      doBeforeCheck();
      WriteAction.run(() -> {
        testDictDir.copy(this, tempDir, TEST_DIC_DIR); // to revert it back
        testDictDir.delete(this);
      });
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> {
        tempDir.findChild(TEST_DIC_DIR).copy(this, testDir, TEST_DIC_DIR);
        tempDir.delete(this);
      });
    }
  }

  public void testAddDictDir() throws IOException {
    final VirtualFile testDir = VfsUtil.findFileByIoFile(new File(getTestDictDirectory()), true);
    final VirtualFile tempDir = VfsUtil.findFileByIoFile(FileUtil.createTempDirectory(TEST_DIC_DIR, TEMP), true);
    WriteAction.run(() -> testDir.findChild(TEST_DIC_AFTER).copy(this, tempDir, TEST_DIC));
    try {
      doBeforeCheck();
      WriteAction.run(() -> tempDir.copy(this, testDir, TEST_DIC_DIR));
      doAfterCheck();
    }
    finally {
      //back to initial state
      WriteAction.run(() -> {
        testDir.findChild(TEST_DIC_DIR).delete(this);
        tempDir.delete(this);
      });
    }
  }
}
