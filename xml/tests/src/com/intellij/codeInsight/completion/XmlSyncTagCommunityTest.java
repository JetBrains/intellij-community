/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiFile;

/**
 * @author Dennis.Ushakov
 */
public class XmlSyncTagCommunityTest extends XmlSyncTagTest {
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

  public void testMultiCaretAdding() {
    doTest("<div<caret>></div>\n" +
           "<div></div>\n", "\b\b\biii",
           "<iii></iii>\n" +
           "<div></div>\n");
    myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(1, 4));
    type("\b");
    myFixture.checkResult("<ii></ii>\n" +
                          "<di></di>\n");
  }

  public void testAfterUndo() {
    doTest("<div class=\"container\">\n" +
           "    <div class=\"row\">\n" +
           "        <div class=\"col-xs-2\"></div>\n" +
           "        <<selection>div</selection> class=\"col-xs-10\"></div>\n" +
           "    </div>\n" +
           "</div>",
           "a",
           "<div class=\"container\">\n" +
           "    <div class=\"row\">\n" +
           "        <div class=\"col-xs-2\"></div>\n" +
           "        <a class=\"col-xs-10\"></a>\n" +
           "    </div>\n" +
           "</div>");
    myFixture.performEditorAction(IdeActions.ACTION_UNDO);
    type("a");
    myFixture.checkResult("<div class=\"container\">\n" +
                          "    <div class=\"row\">\n" +
                          "        <div class=\"col-xs-2\"></div>\n" +
                          "        <a class=\"col-xs-10\"></a>\n" +
                          "    </div>\n" +
                          "</div>");
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

  public void testCompletionWithLookupAfterTyping() {
    doTestCompletion("<html><body></body><bertran></bertran><b<caret>></b><html>", "e",
                     "<html><body></body><bertran></bertran><be></be><html>");
    assertNotNull(myFixture.getLookup());
  }

  public void testSave() {
    doTest("<div>     \n    \n</div><caret>", "\n", "<div>     \n    \n</div>\n");
    final PsiFile file = myFixture.getFile();
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getDocument(file).getTextLength() - 2);
    file.getVirtualFile().putUserData(TrailingSpacesStripper.OVERRIDE_STRIP_TRAILING_SPACES_KEY,
                                                     EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
    FileDocumentManager.getInstance().saveDocument(myFixture.getDocument(file));
    myFixture.checkResult("<div>\n\n</div>\n");
  }

  public void testUndo() {
    doTest("<div<caret>></div>", "v", "<divv></divv>");
    myFixture.performEditorAction(IdeActions.ACTION_UNDO);
    myFixture.checkResult("<div></div>");
  }

  public void testDeletingIncorrectTag() {
    doTest("<div>text</span><caret></div>", "\b\b\b\b\b\b\b", "<div>text</div>");
  }

  public void testEndTagEnd() {
    doTest("<div></div><caret></div>", "\b\b\b\b\b\b", "<div></div>");
  }

  public void testDoubleColonError() {
    doTest("<soap:some<caret>:some></soap:some:some>", "a", "<soap:somea:some></soap:somea:some>");
  }

  public void testMultipleEditors() {
    myFixture.configureByText(XmlFileType.INSTANCE, "<div<caret>></div>");
    final Editor editor = EditorFactory.getInstance().createEditor(myFixture.getEditor().getDocument());
    EditorFactory.getInstance().releaseEditor(editor);
    type("v");
    myFixture.checkResult("<divv></divv>");
  }
}
