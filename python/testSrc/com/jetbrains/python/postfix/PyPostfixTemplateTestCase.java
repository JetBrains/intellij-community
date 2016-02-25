/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.postfix;

import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NonNls;

public abstract class PyPostfixTemplateTestCase extends PyTestCase {
  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.type("\t");
    myFixture.checkResultByFile(getTestName(true) + "_after" + ".py", true);
  }

  abstract protected String getTestDataDir();

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/postfix/" + getTestDataDir();
  }
}
