/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class PropertiesModificationTest extends LightCodeInsightFixtureTestCase {

  public void testValueEditing() {
    doTestEditing(" # comment<caret>", false);
  }

  public void testKeyEditing() {
    doTestEditing("key<caret> = value", true);
  }

  public void testCommentEditing() {
    doTestEditing("key = value<caret>", false);
  }

  public void testKeyReplacement() {
    doTestReplacement("<selection>key</selection> = value", true);
  }

  public void testValueReplacement() {
    doTestReplacement("key = <selection>value</selection>", false);
  }

  public void testCommentReplacement() {
    doTestReplacement("key = value \n# <selection>comment</selection> \n key2 = value2", false);
  }

  public void testKeyDeletion() {
    doTestDeletion("<selection>key</selection> = value", true);
  }

  public void testPropertiesDeletion() {
    doTestDeletion("ke<selection>y1 = value1 \n" +
                   "key2 = value2 \n" +
                   "key3 = value3 \n" +
                   "key4 = value4 \n" +
                   "key5 = val</selection>ue5 \n", true);
  }

  private void doTestEditing(@NotNull String text, boolean isOutOfBlockModificationExpected) {
    doTest(text, () -> myFixture.type("xxx"), isOutOfBlockModificationExpected);
  }

  private void doTestReplacement(@NotNull String text, boolean isOutOfBlockModificationExpected) {
    doTest(text, () -> WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      SelectionModel sel = getEditor().getSelectionModel();
      getEditor().getDocument().replaceString(sel.getSelectionStart(), sel.getSelectionEnd(), "xxx");
    }), isOutOfBlockModificationExpected);
  }

  private void doTestDeletion(@NotNull String text, boolean isOutOfBlockModificationExpected) {
    doTest(text, () -> WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      SelectionModel sel = getEditor().getSelectionModel();
      getEditor().getDocument().deleteString(sel.getSelectionStart(), sel.getSelectionEnd());
    }), isOutOfBlockModificationExpected);
  }

  private void doTest(@NotNull String text, Runnable modificationAction, boolean isOutOfBlockModificationExpected) {
    myFixture.configureByText("test.properties", text);
    PsiModificationTracker tracker = myFixture.getPsiManager().getModificationTracker();
    long oldMod = tracker.getOutOfCodeBlockModificationCount();
    modificationAction.run();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    long newMod = tracker.getOutOfCodeBlockModificationCount();
    assertTrue(isOutOfBlockModificationExpected ^ oldMod == newMod);
  }
}
