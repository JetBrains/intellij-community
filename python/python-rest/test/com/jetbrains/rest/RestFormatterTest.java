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
package com.jetbrains.rest;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.rest.fixtures.RestFixtureTestCase;

public class RestFormatterTest extends RestFixtureTestCase {

  public void testDirective() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".rst");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
      PsiFile file = myFixture.getFile();
      codeStyleManager.reformat(file);
    });
    myFixture.checkResultByFile("formatter/" + getTestName(true) + "_after.rst");
  }
}
