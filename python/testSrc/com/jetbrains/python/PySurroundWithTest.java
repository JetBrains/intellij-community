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
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyTestCase;
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
  public void testCustomFoldingRegionFirstMethod() throws Exception {
    doTestSurroundWithCustomFoldingRegion();
  }

  // PY-11357
  public void testCustomFoldingRegionLastMethod() throws Exception {
    doTestSurroundWithCustomFoldingRegion();
  }

  // PY-14261
  public void testCustomFoldingRegionPreservesIndentation() throws Exception {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSingleCharacter() throws Exception {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSingleStatementInFile() throws Exception {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionIllegalSelection() throws Exception {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSeveralMethods() throws Exception {
    doTestSurroundWithCustomFoldingRegion();
  }

  private void doTestSurroundWithCustomFoldingRegion() throws Exception {
    final Surrounder surrounder = ContainerUtil.find(CustomFoldingSurroundDescriptor.SURROUNDERS, new Condition<Surrounder>() {
      @Override
      public boolean value(Surrounder surrounder) {
        return surrounder.getTemplateDescription().contains("<editor-fold");
      }
    });
    assertNotNull(surrounder);
    doTest(surrounder);
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
