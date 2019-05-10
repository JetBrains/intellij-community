// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actions.CaretStopOptions;
import com.intellij.openapi.editor.actions.CaretStopOptionsTransposed;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.jetbrains.python.fixtures.PyTestCase;

public class PyNextPrevWordTest extends PyTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CaretStopOptions originalCaretStopOptions = EditorSettingsExternalizable.getInstance().getCaretStopOptions();
    EditorSettingsExternalizable.getInstance().setCaretStopOptions(CaretStopOptionsTransposed.DEFAULT_IDEA_BEFORE_192.toCaretStopOptions());
    disposeOnTearDown(() -> EditorSettingsExternalizable.getInstance().setCaretStopOptions(originalCaretStopOptions));
  }

  public void testLineBoundary() {
    myFixture.configureByText("test.py", "def blah():<caret>\n    pass");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("def blah():\n    <caret>pass");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("def blah():<caret>\n    pass");
  }
}
