// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.resolve;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class YAMLRenameTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/resolve/data/";
  }

  public void testAnchorsAndAliases() {
    doSuccessfulTest("zzz");
  }

  public void testPossibleRename1() {
    doSuccessfulTest("zzz");
  }

  public void testPossibleRename2() {
    doSuccessfulTest("zzz");
  }

  public void testConflictPostTest() {
    doConflictTest("post");
  }

  public void testConflictPrevTest() {
    doConflictTest("prev");
  }

  private void doSuccessfulTest(@NotNull String newName) {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".yml");
    myFixture.testRename(testName + ".rename.txt", newName);
  }

  private void doConflictTest(@NotNull String newName) {
    String testName = getTestName(true);
    String answerFilePath = getTestDataPath() + testName + ".conflicts.txt";
    myFixture.configureByFile(testName + ".yml");
    try {
      myFixture.renameElementAtCaret(newName);
      fail("Expected conflicts: \n" + FileUtil.loadFile(new File(answerFilePath)));
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      // exception expected
      assertSameLinesWithFile(answerFilePath, e.getMessage());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
