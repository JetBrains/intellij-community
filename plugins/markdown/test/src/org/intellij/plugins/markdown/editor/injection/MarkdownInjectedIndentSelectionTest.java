// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.injection;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.InjectionTestFixture;
import com.intellij.json.JsonLanguage;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;

public class MarkdownInjectedIndentSelectionTest extends BasePlatformTestCase {
  public void testIndentAndUnindentSelectionInsideInjectedFence() {
    myFixture.configureByText(MarkdownFileType.INSTANCE, """
      ```json
      {
      <selection>"id": "5b975bc6-3871-11ea-a137-2e728ce88125",
      "title": "Top Notch HipKnot Shirt",
      "unit_cost": 5.99<caret></selection>
      }
      ```""");

    new InjectionTestFixture(myFixture).assertInjectedLangAtCaret("JSON");

    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    assertEquals(4, settings.getCommonSettings(MarkdownLanguage.INSTANCE).getIndentOptions().INDENT_SIZE);
    assertEquals(2, settings.getCommonSettings(JsonLanguage.INSTANCE).getIndentOptions().INDENT_SIZE);

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_INDENT_SELECTION);
    myFixture.checkResult("""
      ```json
      {
        "id": "5b975bc6-3871-11ea-a137-2e728ce88125",
        "title": "Top Notch HipKnot Shirt",
        "unit_cost": 5.99
      }
      ```""");
  }
}
