// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.MoveCaretLeftAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

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
    doTest("""
             <div<caret>></div>
             <div<caret>></div>
             """, "v",
           """
             <divv></divv>
             <divv></divv>
             """);
  }

  public void testMultiCaretNested() {
    doTest("""
             <div<caret>>
             <div<caret>></div>
             </div>""", "v",
           """
             <divv>
             <divv></divv>
             </divv>""");
  }

  public void testMultiCaretAdding() {
    doTest("""
             <div<caret>></div>
             <div></div>
             """, "\b\b\biii",
           """
             <iii></iii>
             <div></div>
             """);
    myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(1, 4));
    type("\b");
    myFixture.checkResult("""
                            <ii></ii>
                            <di></di>
                            """);
  }

  public void testAfterUndo() {
    doTest("""
             <div class="container">
                 <div class="row">
                     <div class="col-xs-2"></div>
                     <<selection>div</selection> class="col-xs-10"></div>
                 </div>
             </div>""",
           "a",
           """
             <div class="container">
                 <div class="row">
                     <div class="col-xs-2"></div>
                     <a class="col-xs-10"></a>
                 </div>
             </div>""");
    myFixture.performEditorAction(IdeActions.ACTION_UNDO);
    type("a");
    myFixture.checkResult("""
                            <div class="container">
                                <div class="row">
                                    <div class="col-xs-2"></div>
                                    <a class="col-xs-10"></a>
                                </div>
                            </div>""");
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
    final EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    String stripTrailingSpaces = editorSettings.getStripTrailingSpaces();
    editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
    try {
      FileDocumentManager.getInstance().saveDocument(myFixture.getDocument(file));
    }
    finally {
      editorSettings.setStripTrailingSpaces(stripTrailingSpaces);
    }
    myFixture.checkResult("<div>\n\n</div>\n");
  }

  public void testUndo() {
    doTest("<div<caret>></div>", "v", "<divv></divv>");
    myFixture.performEditorAction(IdeActions.ACTION_UNDO);
    myFixture.checkResult("<div></div>");
  }

  public void testWordExpand() {
    myFixture.configureByText(XmlFileType.INSTANCE, "<di<caret>v></div>");
    myFixture.performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION);
    myFixture.checkResult("<divv></divv>");
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

  public void testDoNotFireDocumentChangeEventIfTagWasNotChanged() {
    myFixture.configureByText(XmlFileType.INSTANCE, "<di<caret>></di>");
    type("v");
    Ref<Boolean> eventSent = Ref.create(false);
    myFixture.getEditor().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        eventSent.set(true);
      }
    }, myFixture.getTestRootDisposable());
    myFixture.testAction(new MoveCaretLeftAction());
    assertFalse(eventSent.get());
  }

  public void testCompleteSingleVariant() {
    doTestCompletion("<htm<caret> xmlns='http://www.w3.org/1999/xhtml'></htm>", null,
                     "<html<caret> xmlns='http://www.w3.org/1999/xhtml'></html>");
  }

  public void testEmptyTagStartSync() {
    myFixture.configureByText("foo.html","<div>ab<<caret>>cd</>ef</div>");
    myFixture.type("div");
    myFixture.checkResult("<div>ab<div>cd</div>ef</div>");
  }

  public void testEmptyTagEndSync() {
    myFixture.configureByText("foo.html","<div>ab<>cd</<caret>>ef</div>");
    myFixture.type("div");
    myFixture.checkResult("<div>ab<div>cd</div>ef</div>");
  }

  public void testEmptyEndSync() {
    myFixture.configureByText("foo.html","<b>ab</<caret>>cd</b>");
    myFixture.type("br");
    myFixture.checkResult("<b>ab</br>cd</b>");
  }

}
