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

import com.intellij.openapi.actionSystem.IdeActions;

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
}
