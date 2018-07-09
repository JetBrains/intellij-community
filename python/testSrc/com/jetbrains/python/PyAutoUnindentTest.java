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

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * Checks auto-unindenting of 'else' and friends.
 * User: dcheryasov
 */
public class PyAutoUnindentTest extends PyTestCase {

  public void testSingleElse() {
    doTypingTest();
  }

  public void testNestedElse() {
    doTypingTest();
  }

  public void testMisplacedElse() {
    doTypingTest();
  }

  public void testSimpleElif() {
    doTypingTest();
  }

  public void testInnerElif() {
    doTypingTest();
  }

  public void testSimpleExcept() {
    doTypingTest();
  }

  public void testSimpleFinally() {
    doTypingTest();
  }

  public void testNestedFinally() {
    doTypingTest();
  }

  /* does not complete keywords
  public void testNestedFinallyCompleted() throws Exception{
    doCompletionTest();
  }
  */



  private void doTypingTest() {
    final String testName = "editing/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.type(':');
    myFixture.checkResultByFile(testName + ".after.py");
  }
}
