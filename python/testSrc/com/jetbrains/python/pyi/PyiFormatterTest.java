// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pyi;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class PyiFormatterTest extends PyTestCase {

  @NotNull
  private PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }

  private void doTest() {
    myFixture.configureByFile("/pyi/formatter/" + getTestName(true) + ".pyi");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
      PsiFile file = myFixture.getFile();
      codeStyleManager.reformat(file);
    });
    myFixture.checkResultByFile("/pyi/formatter/" + getTestName(true) + "_after.pyi");
  }

  // PY-52748
  public void testNewLineAfterColonDoesNotAffectSingleClauseStatementsInStubs() {
    getPythonCodeStyleSettings().NEW_LINE_AFTER_COLON = true;
    doTest();
  }

  // PY-52748
  public void testNewLineAfterColonAffectsMultiClauseStatementsInStubs() {
    getPythonCodeStyleSettings().NEW_LINE_AFTER_COLON = true;
    doTest();
  }
}
