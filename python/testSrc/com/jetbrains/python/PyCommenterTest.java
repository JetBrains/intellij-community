// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.actionSystem.IdeActions;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;


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
