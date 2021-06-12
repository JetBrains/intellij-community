// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;


public class PySelectWordTest extends PyTestCase {
  public void testWord() {
    doTest();
  }

  public void testSlice() {   // PY-288
    doTest();
  }

  public void testLiteral() {   // PY-1489
    doTest();
  }

  public void testList() {   // PY-1686
    doTest();
  }

  public void testComma() {   // PY-1378  
    doTest();
  }

  public void testEscapeSequence() {  // PY-9014
    doTest();
  }

  public void testEscapeSequenceRaw() {  // PY-10322
    doTest();
  }

  public void testInsideEscapeSequence() {  // PY-9014
    doTest();
  }

  private void doTest() {
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, "selectWord/" + getTestName(true), "py");
  }
}
