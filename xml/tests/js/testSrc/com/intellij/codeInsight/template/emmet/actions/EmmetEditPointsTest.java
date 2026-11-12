// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.actions;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.actions.EmmetEditPointsTestUtil.assertCarets;
import static com.intellij.codeInsight.template.emmet.actions.EmmetEditPointsTestUtil.moveBackward;
import static com.intellij.codeInsight.template.emmet.actions.EmmetEditPointsTestUtil.moveForward;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * @author Dennis.Ushakov
 */
@TestApplication
public class EmmetEditPointsTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testForward() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(HtmlFileType.INSTANCE, "<caret><a href=\"\">\n\t<b></b>\n\t\n</a>");
    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();
      assertCarets(editor, 0);

      moveForward(editor, 9);  // Moved caret into empty attribute
      moveForward(editor, 16); // Moved caret into <b> tag
      moveForward(editor, 22); // Moved caret into empty attribute
      moveForward(editor, 22); // No movement
    });
  }

  @Test
  public void testBackward() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(HtmlFileType.INSTANCE, "<a href=\"\">\n\t<b></b>\n\t\n</a><caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();
      assertCarets(editor, editor.getDocument().getTextLength());

      moveBackward(editor, 22); // Moved caret into empty line
      moveBackward(editor, 16); // Moved caret into <b> tag
      moveBackward(editor, 9);  // Moved caret into empty attribute
      moveBackward(editor, 9);  // No movement
    });
  }

  @Test
  public void testWhitespaceForward() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(HtmlFileType.INSTANCE, "<caret><a>\n\t\n\t\n</a>");
    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();
      assertCarets(editor, 0);

      moveForward(editor, 5);
      moveForward(editor, 7);
    });
  }

  @Test
  public void testWhitespaceBackward() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(HtmlFileType.INSTANCE, "<a>\n\t\n\t\n</a><caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();
      assertCarets(editor, editor.getDocument().getTextLength());

      moveBackward(editor, 7);
      moveBackward(editor, 5);
    });
  }

  @Test
  public void testMultiCursorForward() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(HtmlFileType.INSTANCE,
                              """
                                <ul>
                                    <li><caret><a href=""></a></li>
                                    <li><caret><a href=""></a></li>
                                </ul>
                                """);
    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();
      assertCarets(editor, 13, 42);

      moveForward(editor, 22, 51);
      moveForward(editor, 24, 53);
      moveForward(editor, 28, 57);
      moveForward(editor, 42, 57);
      moveForward(editor, 51, 57);
      moveForward(editor, 53, 57);
      moveForward(editor, 57);
    });
  }

  @Test
  public void testMultiCursorBackward() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(HtmlFileType.INSTANCE,
                              """
                                <ul>
                                    <li><a href=""></a><caret></li>
                                    <li><a href=""></a><caret></li>
                                </ul>
                                """);
    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();
      assertCarets(editor, 28, 57);

      moveBackward(editor, 24, 53);
      moveBackward(editor, 22, 51);
      moveBackward(editor, 13, 42);
      moveBackward(editor, 13, 28);
      moveBackward(editor, 13, 24);
      moveBackward(editor, 13, 22);
      moveBackward(editor, 13);
    });
  }

}
