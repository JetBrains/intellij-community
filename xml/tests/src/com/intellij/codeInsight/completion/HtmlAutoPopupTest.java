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
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase;
import com.intellij.util.containers.ContainerUtil;

public class HtmlAutoPopupTest extends CompletionAutoPopupTestCase {

  public void testAfterTagOpen() {
    doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "<");
    myFixture.type("blockq\n");
    myFixture.checkResult("<div><blockquote<caret></div>");
  }

  public void testAfterTagOpenWithPrefix() {
    doTestPopup(HtmlFileType.INSTANCE, "<div>foo<caret></div>", "<");
    myFixture.type("blockq\n");
    myFixture.checkResult("<div>foo<blockquote<caret></div>");
  }

  public void testAfterTagOpenWithSuffix() {
    doTestPopup(HtmlFileType.INSTANCE, "<div><caret>foo</div>", "<");
    myFixture.type("blockq\n");
    myFixture.checkResult("<div><blockquote<caret>foo</div>");
  }

  public void testDoNotShowPopupInTextXhtml() {
    doTestNoPopup(XHtmlFileType.INSTANCE, "<div><caret></div>", "p");
  }

  public void testDoNotShowPopupAfterSpace() {
    doTestNoPopup(HtmlFileType.INSTANCE, "<div>foo<caret></div>", " ");
    type("a");
    assertNotNull(getLookup());
  }

  public void testAfterAmpersand() {
    doTestPopup(HtmlFileType.INSTANCE, "<div>&<caret></div>", "s");
    myFixture.type("t");
    type("a");
    myFixture.type("\n");
    myFixture.checkResult("<div>&star;</div>");
  }

  public void testAfterAmpersandEmptyFile() {
    doTestPopup(HtmlFileType.INSTANCE, "&<caret>", "s");
    assertNull(getLookup().getCurrentItemOrEmpty());
    myFixture.type("t");
    type("a");
    myFixture.type("\n");
    myFixture.checkResult("&star;");
  }

  public void testAfterAmpersandZeroChars() {
    doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "&");
    myFixture.type("\n");
    myFixture.checkResult("<div>&\n</div>");
  }

  public void testAfterAmpersandOneChar() {
    doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "&t");
    myFixture.type("\n");
    myFixture.checkResult("<div>&t\n</div>");
  }

  public void testAfterAmpersandTwoChars() {
    doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "&ta");
    myFixture.type("\n");
    myFixture.checkResult("<div>&target;</div>");
  }

  public void testAfterAmpersandContents() {
    doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "&bb");
    assertSameElements(myFixture.getLookupElementStrings(), "bbrk", "bbrktbrk");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "bbrk", "bbrktbrk", "lbbrk", "rbbrk");
  }

  public void testAfterAmpersandWithPrefix() {
    doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "the&n");
    myFixture.type("bs\n");
    myFixture.type("foo&st");
    type("a");
    myFixture.type("\n");
    myFixture.checkResult("<div>the&nbsp;foo&star;</div>");
  }

  public void testAmpersandInsideEmptyAttributeValue() {
    doTestPopup(HtmlFileType.INSTANCE, "<div title='<caret>'></div>", "&tar");
    myFixture.type("\n");
    myFixture.checkResult("<div title='&target;'></div>");
  }

  public void testAmpersandZeroCharsInsideAttributeValue() {
    doTestPopup(HtmlFileType.INSTANCE, "<div title='<caret>'></div>", "&");
    myFixture.type("\n");
    myFixture.checkResult("<div title='&\n'></div>");
  }

  public void testAmpersandOneCharInsideAttributeValue() {
    doTestPopup(HtmlFileType.INSTANCE, "<div title='<caret>'></div>", "&t");
    myFixture.type("\n");
    myFixture.checkResult("<div title='&t\n'></div>");
  }

  public void testAmpersandTwoCharsInsideAttributeValue() {
    doTestPopup(HtmlFileType.INSTANCE, "<div title='<caret>'></div>", "&ta");
    myFixture.type("\n");
    myFixture.checkResult("<div title='&target;'></div>");
  }

  public void testAmpersandInsideAttributeValue() {
    doTestPopup(HtmlFileType.INSTANCE, "<div title='v<caret>a'></div>", "&tar");
    myFixture.type("\n");
    myFixture.checkResult("<div title='v&target;a'></div>");
  }

  public void testDoNotShowPopupAfterQuotedSymbolXhtml() {
    doTestNoPopup(XHtmlFileType.INSTANCE, "<div>\"<caret></div>", "s");
  }

  public void testAfterTagOpenXhtml() {
    doTestPopup(XHtmlFileType.INSTANCE, "<div><caret></div>", "<");
  }

  public void testTypingInHtmlText() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<div>\n  <caret>\n</div>");
    myFixture.type("checkAmp&n");
    type("bs\n");
    type("blockq\n");
    type("blockq");
    EditorActionManager.getInstance();
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    type("\n");
    myFixture.checkResult("<div>\n  checkAmp&nbsp;blockq\n    <blockquote<caret>\n</div>");
  }

  public void testTypingInHtmlText2() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<div>The<caret></div>");
    type(" p");
    myFixture.type("\n");
    myFixture.checkResult("<div>The p\n</div>");
  }

  public void testTypingInHtmlTextEmptyFile() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<caret>");
    type("p");
    assertNull(getLookup().getCurrentItemOrEmpty());
    myFixture.type("\n");
    myFixture.checkResult("p\n");
  }

  public void testTypingInHtmlDisabledAutopopup() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<caret>");
    try {
      HtmlSettings.getInstance().AUTO_POPUP_TAG_CODE_COMPLETION_ON_TYPING_IN_TEXT = false;
      type("p");
      assertNull(getLookup());
      myFixture.completeBasic();
      assertContainsElements(myFixture.getLookupElementStrings(), "<pre", "<p", "<picture");
    } finally {
      HtmlSettings.getInstance().AUTO_POPUP_TAG_CODE_COMPLETION_ON_TYPING_IN_TEXT = true;
    }
  }

  public void testStartsWithCharMatchingAutoPopup() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "<html lang='en'><caret>");
    type("la");
    assertSameElements(ContainerUtil.map(getLookup().getItems(), i -> i.getLookupString()), "<label");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "lang", "<label", "<template");
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

