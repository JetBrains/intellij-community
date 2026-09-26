// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.ParsingTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public abstract class PythonIncrementalParsingTestCase extends PyTestCase {

  private static final String STATEMENTS_REGISTRY_KEY = "python.statement.lists.incremental.reparse";
  private static final String AST_LEAVES_REGISTRY_KEY = "python.ast.leaves.incremental.reparse";

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
    boolean statementListsRegistryFlag = Registry.is(STATEMENTS_REGISTRY_KEY);
    boolean leavesRegistryFlag = Registry.is(AST_LEAVES_REGISTRY_KEY);

    var sourceFileName = sourceFileBaseName + "/" + sourceFileBaseName + getFileExtension();
    myFixture.configureByFile(sourceFileName);
    var newTextFileName = sourceFileBaseName + "/" + newTextFileBaseName + ".new";
    String newText;
    try {
      Registry.get(STATEMENTS_REGISTRY_KEY).setValue(true);
      Registry.get(AST_LEAVES_REGISTRY_KEY).setValue(true);

      newText = FileUtil.loadFile(new File(getTestDataPath(), newTextFileName)).replace("\r", "");
      ParsingTestUtil.testIncrementalParsing(myFixture.getFile(), newText, getAnswersFilePath(),
                                             checkInitialTreeForErrors, checkFinalTreeForErrors);
    }
    catch (IOException e) {
      fail(e.getMessage());
      throw new RuntimeException();
    }
    finally {
      Registry.get(STATEMENTS_REGISTRY_KEY).setValue(statementListsRegistryFlag);
      Registry.get(AST_LEAVES_REGISTRY_KEY).setValue(leavesRegistryFlag);
    }
  }

  protected String getAnswersFilePath() {
    return FileUtil.join(getTestDataPath() + "/" + getTestName(true), "after", getTestName(true) + ".txt");
  }
}
