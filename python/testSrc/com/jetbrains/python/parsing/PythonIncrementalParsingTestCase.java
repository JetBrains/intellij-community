// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.ParsingTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public abstract class PythonIncrementalParsingTestCase extends PyTestCase {

  protected @NotNull String getFileExtension() {
    return ".py";
  }

  protected void doTest() {
    doTest(true, true);
  }

  protected void doTest(@NotNull String sourceFileBaseName) {
    doTest(sourceFileBaseName, true, true);
  }

  protected void doTest(boolean checkInitialTreeForErrors, boolean checkFinalTreeForErrors) {
    doTest(getTestName(true), checkInitialTreeForErrors, checkFinalTreeForErrors);
  }

  protected void doTest(@NotNull String sourceFileBaseName, boolean checkInitialTreeForErrors, boolean checkFinalTreeForErrors) {
    doTest(sourceFileBaseName, getTestName(true), checkInitialTreeForErrors, checkFinalTreeForErrors);
  }

  protected void doTest(@NotNull String sourceFileBaseName,
                        @NotNull String newTextFileBaseName,
                        boolean checkInitialTreeForErrors,
                        boolean checkFinalTreeForErrors) {
    var sourceFileName = sourceFileBaseName + "/" + sourceFileBaseName + getFileExtension();
    myFixture.configureByFile(sourceFileName);
    var newTextFileName = sourceFileBaseName + "/" + newTextFileBaseName + ".new";
    String newText;
    try {
      newText = FileUtil.loadFile(new File(getTestDataPath(), newTextFileName)).replace("\r", "");
    }
    catch (IOException e) {
      fail(e.getMessage());
      throw new RuntimeException();
    }

    ParsingTestUtil.testIncrementalParsing(myFixture.getFile(), newText, getAnswersFilePath(),
                                           checkInitialTreeForErrors, checkFinalTreeForErrors);
  }

  protected String getAnswersFilePath() {
    return FileUtil.join(getTestDataPath() + "/" + getTestName(true), "after", getTestName(true) + ".txt");
  }
}
