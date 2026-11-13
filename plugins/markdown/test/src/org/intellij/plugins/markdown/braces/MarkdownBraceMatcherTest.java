// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.braces;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class MarkdownBraceMatcherTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testParenBeforeIdentifier() {
    doTest('(', "<caret>abc", "(abc");
  }

  @Test
  public void testParenBeforeWhiteSpace() {
    doTest('(', "<caret> abc", "() abc");
  }

  @Test
  public void testParenBeforeNewLine() {
    doTest('(', "<caret>\nabc", "()\n" + "abc");
  }

  @Test
  public void testParenAtTheEnd() {
    doTest('(', "<caret>", "()");
  }

  @Test
  public void testBracket() {
    doTest('[', "<caret> abc", "[] abc");
  }

  @Test
  public void testBracketWithSelection() {
    boolean oldSettings = CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED;
    try {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      doTest('[', "<caret><selection>abc</selection>", "[abc]");
    } finally {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = oldSettings;
    }
  }

  public void doTest(char braceToInsert, @NotNull String text, @NotNull String expectedText) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    myFixture.type(braceToInsert);
    myFixture.checkResult(expectedText);
  }
}