// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionSource;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;

public class PyShowIntentionActionsHandlerTest extends PyTestCase {

  public void testCaretRestoredWhenFixOffsetShiftsAfterIntentionModification() {
    final String importToInsert = "import os\n";
    myFixture.configureByText("main.py", "x = 1\nunresolved_ref<caret>()\n");

    final int originalOffset = myFixture.getEditor().getCaretModel().getOffset();
    final int fixOffset = myFixture.getFile().getText().indexOf("unresolved_ref");
    assertTrue(fixOffset >= 0);

    final IntentionAction insertingIntention = new IntentionAction() {
      @Override
      public @NotNull String getText() {
        return "insert import";
      }

      @Override
      public @NotNull String getFamilyName() {
        return getText();
      }

      @Override
      public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
        editor.getDocument().insertString(0, importToInsert);
      }

      @Override
      public boolean startInWriteAction() {
        return true;
      }
    };

    final boolean invoked = ShowIntentionActionsHandler.chooseActionAndInvoke(
      myFixture.getFile(),
      myFixture.getEditor(),
      insertingIntention,
      "test",
      fixOffset,
      IntentionSource.OTHER
    );

    assertTrue(invoked);
    assertEquals(originalOffset + importToInsert.length(), myFixture.getEditor().getCaretModel().getOffset());
  }
}
