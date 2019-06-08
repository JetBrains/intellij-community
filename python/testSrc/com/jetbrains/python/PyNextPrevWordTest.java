// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.actionSystem.IdeActions;
import com.jetbrains.python.fixtures.PyTestCase;

public class PyNextPrevWordTest extends PyTestCase {
  public void testLineBoundary() {
    myFixture.configureByText("test.py", "def blah():<caret>\n    pass");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("def blah():\n<caret>    pass");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("def blah():\n    pass<caret>");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("def blah():\n    <caret>pass");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("def blah():<caret>\n    pass");
  }
}
