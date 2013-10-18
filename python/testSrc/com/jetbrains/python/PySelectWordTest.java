package com.jetbrains.python;

import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
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

  private void doTest() {
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, "selectWord/" + getTestName(true), "py");
  }
}
