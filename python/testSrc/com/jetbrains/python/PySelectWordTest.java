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
