/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.folding.CustomFoldingSurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithIfSurrounder;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithTryExceptSurrounder;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithWhileSurrounder;

/**
 * @author yole
 */
public class PySurroundWithTest extends PyTestCase {
  public void testSurroundWithIf() throws Exception {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundWithWhile() throws Exception {
    doTest(new PyWithWhileSurrounder());
  }

  public void testSurroundWithTryExcept() throws Exception {
    doTest(new PyWithTryExceptSurrounder());
  }

  // PY-11357
  public void testSurroundFirstMethodWithCustomFoldingRegion() {
    checkCustomFoldingRegionRange(PyFunction.class);
  }

  // PY-11357
  public void testSurroundLastMethodWithCustomFoldingRegion() {
    checkCustomFoldingRegionRange(PyFunction.class);
  }

  private PsiElement[] checkCustomFoldingRegionRange(Class<? extends PyElement>... elementTypes) {
    myFixture.configureByFile("/surround/" + getTestName(false) + ".py");
    final SelectionModel selection = myFixture.getEditor().getSelectionModel();
    final PsiElement[] range = CustomFoldingSurroundDescriptor.INSTANCE.getElementsToSurround(myFixture.getFile(),
                                                                                              selection.getSelectionStart(),
                                                                                              selection.getSelectionEnd());
    assertEquals(elementTypes.length, range.length);
    for (int i = 0; i < elementTypes.length; i++) {
      assertInstanceOf(range[i], elementTypes[i]);
    }
    return range;
  }

  private void doTest(final Surrounder surrounder) throws Exception {
    String baseName = "/surround/" + getTestName(false);
    myFixture.configureByFile(baseName + ".py");
    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
      }
    }.execute();
    myFixture.checkResultByFile(baseName + "_after.py", true);
  }
}
