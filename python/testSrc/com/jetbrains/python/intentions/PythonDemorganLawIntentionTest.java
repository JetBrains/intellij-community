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

/*
 * User: anna
 * Date: 06-Mar-2008
 */
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;

public class PythonDemorganLawIntentionTest extends PyTestCase {
  public void test1() throws Exception {
    doTest();
  }

  public void test2() throws Exception {
    doTest();
  }

  public void test3() throws Exception {
    doTest();
  }

  public void test4() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    myFixture.configureByFile("before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention("DeMorgan Law");
    myFixture.launchAction(action);
    myFixture.checkResultByFile("after" + getTestName(false) + ".py");

  }
  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/demorgan";
  }
}