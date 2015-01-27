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

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author Dennis.Ushakov
 */
public class XmlSyncTagTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testStartToEnd() {
    doTest("<div<caret>></div>", "v", "<divv></divv>");
  }

  public void testEndToStart() {
    doTest("<div></div<caret>>", "v", "<divv></divv>");
  }

  public void testStartToEndAndEndToStart() {
    doTest("<div<caret>></div>", "v", "<divv></divv>");
    myFixture.getEditor().getCaretModel().moveToOffset(12);
    type("\b");
    myFixture.checkResult("<div></div>");
  }

  public void testLastCharDeleted() {
    doTest("<div<caret>></div>", "\b\b\b", "<></>");
  }

  public void testLastCharDeletedAndNewAdded() {
    doTest("<a<caret> alt='</>'></a>", "\bb", "<b alt='</>'></b>");
  }

  public void testSelection() {
    doTest("<<selection>div</selection>></div>", "b", "<b></b>");
  }

  public void testMultiCaret() {
    doTest("<div<caret>></div>\n" +
           "<div<caret>></div>\n", "v",
           "<divv></divv>\n" +
           "<divv></divv>\n");
  }

  public void testMultiCaretNested() {
    doTest("<div<caret>>\n" +
           "<div<caret>></div>\n" +
           "</div>", "v",
           "<divv>\n" +
           "<divv></divv>\n" +
           "</divv>");
  }

  public void testSpace() {
    doTest("<div<caret>></div>", " ", "<div ></div>");
  }

  public void testRecommence() {
    doTest("<divv<caret>></div>", "\bd", "<divd></divd>");
  }

  public void testCompletionSimple() {
    doTestCompletion("<html><body></body><b<caret>></b><html>", null,
                     "<html><body></body><body></body><html>");
  }

  public void testCompletionWithLookup() {
    doTestCompletion("<html><body></body><bertran></bertran><b<caret>></b><html>", "e\n",
                     "<html><body></body><bertran></bertran><bertran></bertran><html>");
  }

  public void testUndo() {
    doTest("<div<caret>></div>", "v", "<divv></divv>");
    myFixture.performEditorAction(IdeActions.ACTION_UNDO);
    myFixture.checkResult("<div></div>");
  }

  public void testHtmlInJsp() {
    final FileType jsp = FileTypeManager.getInstance().getFileTypeByExtension("jsp");
    doTest(jsp, "<div<caret>></div>", "v", "<divv></divv>");
  }

  public void testJspInJsp() {
    final FileType jsp = FileTypeManager.getInstance().getFileTypeByExtension("jsp");
    doTest(jsp, "<p:div<caret>></p:div>", "v", "<p:divv></p:divv>");
  }

  public void testInjectionInHtml() {
    doTest(HtmlFileType.INSTANCE, "<script>var a ='<div<caret>></div>'</script>", "v", "<script>var a ='<divv></divv>'</script>");
  }

  public void testInjectionInJS() {
    final FileType js = FileTypeManager.getInstance().getFileTypeByExtension("js");
    doTest(js, "var a ='<div<caret>></div>'", "v", "var a ='<divv></divv>'");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    WebEditorOptions.getInstance().setSyncTagEditing(true);
    myFixture.setCaresAboutInjection(false);
  }

  @Override
  protected void tearDown() throws Exception {
    WebEditorOptions.getInstance().setSyncTagEditing(false);
    super.tearDown();
  }

  private void doTest(final String text, final String toType, final String result) {
    doTest(XmlFileType.INSTANCE, text, toType, result);
  }

  private void doTest(final FileType fileType, final String text, final String toType, final String result) {
    myFixture.configureByText(fileType, text);
    type(toType);
    myFixture.checkResult(result);
  }

  private void type(String toType) {
    for (int i = 0; i < toType.length(); i++) {
      final char c = toType.charAt(i);
      CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              myFixture.type(c);
            }
          });
        }
      }, "Typing", DocCommandGroupId.noneGroupId(myFixture.getEditor().getDocument()), myFixture.getEditor().getDocument());
    }
  }

  private void doTestCompletion(final String text, final String toType, final String result) {
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            myFixture.completeBasic();
            if (toType != null) myFixture.type(toType);
          }
        });
      }
    }, "Typing", DocCommandGroupId.noneGroupId(myFixture.getEditor().getDocument()), myFixture.getEditor().getDocument());
    myFixture.checkResult(result);
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }
}
