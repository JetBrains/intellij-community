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
  public void testSurroundWithIf() {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundWithWhile() {
    doTest(new PyWithWhileSurrounder());
  }

  public void testSurroundWithTryExcept() {
    doTest(new PyWithTryExceptSurrounder());
  }

  // PY-11357
  public void testCustomFoldingRegionFirstMethod() {
    doTestSurroundWithCustomFoldingRegion();
  }

  // PY-11357
  public void testCustomFoldingRegionLastMethod() {
    doTestSurroundWithCustomFoldingRegion();
  }

  // PY-14261
  public void testCustomFoldingRegionPreservesIndentation() {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSingleCharacter() {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSingleStatementInFile() {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionIllegalSelection() {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSeveralMethods() {
    doTestSurroundWithCustomFoldingRegion();
  }

  private void doTestSurroundWithCustomFoldingRegion() {
    final Surrounder surrounder = ContainerUtil.find(CustomFoldingSurroundDescriptor.SURROUNDERS, new Condition<Surrounder>() {
      @Override
      public boolean value(Surrounder surrounder) {
        return surrounder.getTemplateDescription().contains("<editor-fold");
      }
    });
    assertNotNull(surrounder);
    doTest(surrounder);
  }

  public void testSurroundCommentAtStart() {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundCommentAtEnd() {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundNewline() {
    doTest(new PyWithIfSurrounder());
  }

  private void doTest(final Surrounder surrounder) {
    String baseName = "/surround/" + getTestName(false);
    myFixture.configureByFile(baseName + ".py");
    SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
    myFixture.checkResultByFile(baseName + "_after.py", true);
  }
}
