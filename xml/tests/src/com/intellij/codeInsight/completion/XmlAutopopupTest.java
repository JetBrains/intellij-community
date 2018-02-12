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

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.openapi.fileTypes.FileType;

public class XmlAutopopupTest extends CompletionAutoPopupTestCase {
  public void testDoNotShowPopupInText() {
    doTestNoPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "p");
  }

  public void testAfterTagOpen() {
    doTestPopup(HtmlFileType.INSTANCE, "<div><caret></div>", "<");
  }

  public void testDoNotShowPopupInTextXhtml() {
    doTestNoPopup(XHtmlFileType.INSTANCE, "<div><caret></div>", "p");
  }

  public void testAfterAmpersand() {
    doTestPopup(HtmlFileType.INSTANCE, "<div>&<caret></div>", "t");
  }

  public void testDoNotShowPopupAfterQuotedSymbol() {
    doTestNoPopup(HtmlFileType.INSTANCE, "<div>\"<caret></div>", "s");
  }

  public void testAfterTagOpenXhtml() {
    doTestPopup(XHtmlFileType.INSTANCE, "<div><caret></div>", "<");
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

