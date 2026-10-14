/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.application.options.HtmlSettings;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class HtmlAutoPopupTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private CodeInsightTestFixture myFixture;

  @BeforeEach
  void initFixture() {
    myFixture = codeInsightFixture.get();
  }

  private <E extends Throwable> void runTest(@NotNull ThrowableRunnable<E> runnable) throws E {
    CompletionAutoPopupTestUtil.runWithAutoPopupEnabled(myFixture, runnable);
  }

  private void type(@NotNull String s) {
    CompletionAutoPopupTestUtil.type(myFixture, s);
  }

  private @Nullable LookupImpl getLookup() {
    return CompletionAutoPopupTestUtil.getLookup(myFixture);
  }

  @Test
  public void testAfterTagOpen() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "<");
      myFixture.type("blockq\n");
      myFixture.checkResult("<div><blockquote<caret></div>");
    });
  }

  @Test
  public void testAfterTagOpenWithPrefix() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div>foo<caret></div>", "<");
      myFixture.type("blockq\n");
      myFixture.checkResult("<div>foo<blockquote<caret></div>");
    });
  }

  @Test
  public void testAfterTagOpenWithSuffix() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div><caret>foo</div>", "<");
      myFixture.type("blockq\n");
      myFixture.checkResult("<div><blockquote<caret>foo</div>");
    });
  }

  @Test
  public void testDoNotShowPopupInTextXhtml() {
    runTest(() -> {
      doTestNoPopup(XHtmlFileType.INSTANCE, "<div><caret></div>", "p");
    });
  }

  @Test
  public void testDoNotShowPopupAfterSpace() {
    runTest(() -> {
      doTestNoPopup(HtmlFileType.INSTANCE, "<div>foo<caret></div>", " ");
      type("a");
      assertNotNull(getLookup());
    });
  }

  @Test
  public void testAfterAmpersand() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div>&<caret></div>", "s");
      myFixture.type("t");
      type("a");
      myFixture.type("\n");
      myFixture.checkResult("<div>&star;</div>");
    });
  }

  @Test
  public void testAfterAmpersandEmptyFile() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "&<caret>", "s");
      assertNull(getLookup().getCurrentItemOrEmpty());
      myFixture.type("t");
      type("a");
      myFixture.type("\n");
      myFixture.checkResult("&star;");
    });
  }

  @Test
  public void testAfterAmpersandZeroChars() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "&");
      myFixture.type("\n");
      myFixture.checkResult("<div>&\n</div>");
    });
  }

  @Test
  public void testAfterAmpersandOneChar() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "&t");
      myFixture.type("\n");
      myFixture.checkResult("<div>&t\n</div>");
    });
  }

  @Test
  public void testAfterAmpersandTwoChars() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "&ta");
      myFixture.type("\n");
      myFixture.checkResult("<div>&target;</div>");
    });
  }

  @Test
  public void testAfterAmpersandContents() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "&bb");
      assertThat(myFixture.getLookupElementStrings()).containsExactlyInAnyOrder("bbrk", "bbrktbrk");
      myFixture.completeBasic();
      assertThat(myFixture.getLookupElementStrings()).containsExactlyInAnyOrder("bbrk", "bbrktbrk", "lbbrk", "rbbrk");
    });
  }

  @Test
  public void testAfterAmpersandWithPrefix() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "the&n");
      myFixture.type("bs\n");
      myFixture.type("foo&st");
      type("a");
      myFixture.type("\n");
      myFixture.checkResult("<div>the&nbsp;foo&star;</div>");
    });
  }

  @Test
  public void testAmpersandInsideEmptyAttributeValue() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div title='<caret>'></div>", "&tar");
      myFixture.type("\n");
      myFixture.checkResult("<div title='&target;'></div>");
    });
  }

  @Test
  public void testAmpersandZeroCharsInsideAttributeValue() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div title='<caret>'></div>", "&");
      myFixture.type("\n");
      myFixture.checkResult("<div title='&\n'></div>");
    });
  }

  @Test
  public void testAmpersandOneCharInsideAttributeValue() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div title='<caret>'></div>", "&t");
      myFixture.type("\n");
      myFixture.checkResult("<div title='&t\n'></div>");
    });
  }

  @Test
  public void testAmpersandTwoCharsInsideAttributeValue() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div title='<caret>'></div>", "&ta");
      myFixture.type("\n");
      myFixture.checkResult("<div title='&target;'></div>");
    });
  }

  @Test
  public void testAmpersandInsideAttributeValue() {
    runTest(() -> {
      doTestPopup(HtmlFileType.INSTANCE, "<div title='v<caret>a'></div>", "&tar");
      myFixture.type("\n");
      myFixture.checkResult("<div title='v&target;a'></div>");
    });
  }

  @Test
  public void testDoNotShowPopupAfterQuotedSymbolXhtml() {
    runTest(() -> {
      doTestNoPopup(XHtmlFileType.INSTANCE, "<div>\"<caret></div>", "s");
    });
  }

  @Test
  public void testAfterTagOpenXhtml() {
    runTest(() -> {
      doTestPopup(XHtmlFileType.INSTANCE, "<div><caret></div>", "<");
    });
  }

  @Test
  public void testTypingInHtmlText() {
    runTest(() -> {
      myFixture.configureByText(HtmlFileType.INSTANCE, "<div>\n  <caret>\n</div>");
      myFixture.type("checkAmp&n");
      type("bs\n");
      type("blockq\n");
      type("blockq");
      EditorActionManager.getInstance();
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
      type("\n");
      myFixture.checkResult("<div>\n  checkAmp&nbsp;blockq\n    <blockquote<caret>\n</div>");
    });
  }

  @Test
  public void testTypingInHtmlText2() {
    runTest(() -> {
      myFixture.configureByText(HtmlFileType.INSTANCE, "<div>The<caret></div>");
      type(" p");
      myFixture.type("\n");
      myFixture.checkResult("<div>The p\n</div>");
    });
  }

  @Test
  public void testTypingInHtmlTextEmptyFile() {
    runTest(() -> {
      myFixture.configureByText(HtmlFileType.INSTANCE, "<caret>");
      type("p");
      assertNull(getLookup().getCurrentItemOrEmpty());
      myFixture.type("\n");
      myFixture.checkResult("p\n");
    });
  }

  @Test
  public void testTypingInHtmlDisabledAutopopup() {
    runTest(() -> {
      myFixture.configureByText(HtmlFileType.INSTANCE, "<caret>");
      try {
        HtmlSettings.getInstance().AUTO_POPUP_TAG_CODE_COMPLETION_ON_TYPING_IN_TEXT = false;
        type("p");
        assertNull(getLookup());
        myFixture.completeBasic();
        assertThat(myFixture.getLookupElementStrings()).contains("<pre", "<p", "<picture");
      } finally {
        HtmlSettings.getInstance().AUTO_POPUP_TAG_CODE_COMPLETION_ON_TYPING_IN_TEXT = true;
      }
    });
  }

  @Test
  public void testStartsWithCharMatchingAutoPopup() {
    runTest(() -> {
      myFixture.configureByText(HtmlFileType.INSTANCE, "<html lang='en'><caret>");
      type("la");
      assertThat(ContainerUtil.map(getLookup().getItems(), i -> i.getLookupString())).containsExactlyInAnyOrder("<label");
      myFixture.completeBasic();
      assertThat(myFixture.getLookupElementStrings()).containsExactlyInAnyOrder("lang", "<label", "<template");
    });
  }

  private void doTestPopup(FileType fileType, String fileText, String typeString) {
    myFixture.configureByText(fileType, fileText);
    type(typeString);
    assertNotNull(getLookup());
  }

  private void doTestNoPopup(FileType fileType, String fileText, String typeString) {
    myFixture.configureByText(fileType, fileText);
    type(typeString);
    assertNull(getLookup());
  }
}

