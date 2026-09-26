// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.actions;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.editor.Editor;

/**
 * @author Dennis.Ushakov
 */
public class EmmetEditPointsTest extends EmmetEditPointsTestBase {
  public void testForward() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<caret><a href=\"\">\n\t<b></b>\n\t\n</a>");
    final Editor editor = myFixture.getEditor();
    assertCarets(editor, 0);

    moveForward(editor, 9);  // Moved caret into empty attribute
    moveForward(editor, 16); // Moved caret into <b> tag
    moveForward(editor, 22); // Moved caret into empty attribute
    moveForward(editor, 22); // No movement
  }

  public void testBackward() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<a href=\"\">\n\t<b></b>\n\t\n</a><caret>");
    final Editor editor = myFixture.getEditor();
    assertCarets(editor, editor.getDocument().getTextLength());

    moveBackward(editor, 22); // Moved caret into empty line
    moveBackward(editor, 16); // Moved caret into <b> tag
    moveBackward(editor, 9);  // Moved caret into empty attribute
    moveBackward(editor, 9);  // No movement
  }

  public void testWhitespaceForward() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<caret><a>\n\t\n\t\n</a>");
    final Editor editor = myFixture.getEditor();
    assertCarets(editor, 0);

    moveForward(editor, 5);
    moveForward(editor, 7);
  }

  public void testWhitespaceBackward() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<a>\n\t\n\t\n</a><caret>");
    final Editor editor = myFixture.getEditor();
    assertCarets(editor, editor.getDocument().getTextLength());

    moveBackward(editor, 7);
    moveBackward(editor, 5);
  }

  public void testMultiCursorForward() {
    myFixture.configureByText(HtmlFileType.INSTANCE,
                              """
                                <ul>
                                    <li><caret><a href=""></a></li>
                                    <li><caret><a href=""></a></li>
                                </ul>
                                """);
    final Editor editor = myFixture.getEditor();
    assertCarets(editor, 13, 42);

    moveForward(editor, 22, 51);
    moveForward(editor, 24, 53);
    moveForward(editor, 28, 57);
    moveForward(editor, 42, 57);
    moveForward(editor, 51, 57);
    moveForward(editor, 53, 57);
    moveForward(editor, 57);
  }

  public void testMultiCursorBackward() {
    myFixture.configureByText(HtmlFileType.INSTANCE,
                              """
                                <ul>
                                    <li><a href=""></a><caret></li>
                                    <li><a href=""></a><caret></li>
                                </ul>
                                """);
    final Editor editor = myFixture.getEditor();
    assertCarets(editor, 28, 57);

    moveBackward(editor, 24, 53);
    moveBackward(editor, 22, 51);
    moveBackward(editor, 13, 42);
    moveBackward(editor, 13, 28);
    moveBackward(editor, 13, 24);
    moveBackward(editor, 13, 22);
    moveBackward(editor, 13);
  }

}
