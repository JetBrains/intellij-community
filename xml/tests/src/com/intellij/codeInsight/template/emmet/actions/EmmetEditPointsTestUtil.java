// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.actions;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Moves the caret of an editor to the next or the previous Emmet edit point, and checks the caret offsets.
 */
@TestOnly
public final class EmmetEditPointsTestUtil {
  private EmmetEditPointsTestUtil() {
  }

  /**
   * Checks that the carets of {@code editor} are at {@code offsets}, in this order.
   */
  @RequiresEdt
  public static void assertCarets(Editor editor, int... offsets) {
    final List<Caret> carets = editor.getCaretModel().getAllCarets();
    assertEquals(offsets.length, carets.size(), "Carets count mismatch");
    for (int i = 0; i < offsets.length; i++) {
      assertEquals(offsets[i], carets.get(i).getOffset(), "Position mismatch for caret " + i);
    }
  }

  /**
   * Runs the "Previous Emmet Edit Point" action in {@code editor}, and checks that the carets are at {@code expected}.
   */
  @RequiresEdt
  public static void moveBackward(Editor editor, int... expected) {
    EditorTestUtil.executeAction(editor, "EmmetPreviousEditPoint");
    assertCarets(editor, expected);
  }

  /**
   * Runs the "Next Emmet Edit Point" action in {@code editor}, and checks that the carets are at {@code expected}.
   */
  @RequiresEdt
  public static void moveForward(Editor editor, int... expected) {
    EditorTestUtil.executeAction(editor, "EmmetNextEditPoint");
    assertCarets(editor, expected);
  }
}
