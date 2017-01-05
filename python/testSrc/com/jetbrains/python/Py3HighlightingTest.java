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
package com.jetbrains.python;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Py3HighlightingTest extends PyTestCase {

  @NotNull
  private static final String TEST_PATH = "/highlighting/";

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  // PY-20770
  public void testNonEmptyReturnInsideAsyncDef() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest(true, false));
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    myFixture.testHighlighting(checkWarnings, checkInfos, false, TEST_PATH + getTestName(true) + PyNames.DOT_PY);
  }
}
