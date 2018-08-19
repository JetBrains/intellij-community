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

import com.intellij.openapi.actionSystem.IdeActions;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyCommenterTest extends PyTestCase {
  public void testIndentedComment() {
    doTest();
  }

  public void testUncommentWithoutSpace() {
    doTest();
  }

  // PY-20777
  public void testLineCommentInFStringFragment() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  private void doTest() {
    myFixture.configureByFile("commenter/" + getTestName(true) + ".py");
    myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE);
    myFixture.checkResultByFile("commenter/" + getTestName(true) + "_after.py", true);
  }
}
