// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.actions;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public abstract class EmmetEditPointsTestBase extends BasePlatformTestCase {
  protected static void assertCarets(Editor editor, int... offsets) {
    final List<Caret> carets = editor.getCaretModel().getAllCarets();
    assertEquals("Carets count mismatch", offsets.length, carets.size());
    for (int i = 0; i < offsets.length; i++) {
      assertEquals("Position mismatch for caret " + i, offsets[i], carets.get(i).getOffset());
    }
  }

  protected static void moveBackward(Editor editor, int... expected) {
    EditorTestUtil.executeAction(editor, "EmmetPreviousEditPoint");
    assertCarets(editor, expected);
  }

  protected static void moveForward(Editor editor, int... expected) {
    EditorTestUtil.executeAction(editor, "EmmetNextEditPoint");
    assertCarets(editor, expected);
  }
}
