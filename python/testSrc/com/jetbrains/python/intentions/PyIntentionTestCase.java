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
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * User: ktisha
 */
public abstract class PyIntentionTestCase extends PyTestCase {
  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/" + getClass().getSimpleName();
  }


  protected void doIntentionTest(final String hint) {
    final String testFileName = getTestName(true);
    myFixture.configureByFile(testFileName + ".py");
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }

  protected void doNegateIntentionTest(final String hint) {
    final String testFileName = getTestName(true);
    myFixture.configureByFile(testFileName + ".py");
    final IntentionAction intentionAction = myFixture.getAvailableIntention(hint);
    assertNull(intentionAction);
  }
}
