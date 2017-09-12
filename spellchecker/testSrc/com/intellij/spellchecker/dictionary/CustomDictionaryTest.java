/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.dictionary;


import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase;
import com.intellij.spellchecker.settings.SpellCheckerSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class CustomDictionaryTest extends SpellcheckerInspectionTestCase {
  private static final String TEST_DIC = "test.dic";
  private static final String NEW_TEST_DIC = "new_" + TEST_DIC;
  private static final String TEST_DIC_AFTER = TEST_DIC + ".after";
  private static final String TEMP_DIC = TEST_DIC + ".temp";
  private List<String> oldPaths;
  SpellCheckerSettings settings;
  SpellCheckerManager spellCheckerManager;


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    settings = SpellCheckerSettings.getInstance(getProject());
    spellCheckerManager = SpellCheckerManager.getInstance(getProject());
    oldPaths = settings.getDictionaryFoldersPaths();
    settings.setDictionaryFoldersPaths(Collections.singletonList(getTestDictDirectory()));
    spellCheckerManager.fullConfigurationReload();
  }

  @Override
  protected void tearDown() throws Exception {
    //noinspection SuperTearDownInFinally
    super.tearDown();
    settings.setDictionaryFoldersPaths(oldPaths);
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
    return findFileByIoFile(Paths.get(getTestDictionary()).toFile(), true);
  }

  private String loadFromTestDictionary() throws IOException {
    final VirtualFile file = findFileByIoFile(new File(getTestDictionary()), true);
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
      modifyDictContent(VfsUtilCore.loadText(findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC_AFTER).toFile(), true)));
      doAfterCheck();
    }
    finally {
      //back to initial state
      modifyDictContent(oldDictContent);
    }
  }

  private void doNewDictTest() throws IOException {
    final VirtualFile file = findFileByIoFile(Paths.get(getTestDictDirectory(), TEST_DIC_AFTER).toFile(), true);
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
      WriteAction.run(() -> findFileByIoFile(Paths.get(getTestDictDirectory(), TEMP_DIC).toFile(), true).rename(this, TEST_DIC));
    }
  }

  public void testAddDictionary() throws IOException, InterruptedException {
    doNewDictTest();
  }

  public void testAddOneMoreDictionary() throws IOException, InterruptedException {
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
}
