// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.impl.DefaultRawTypedHandler;
import com.intellij.openapi.editor.impl.EditorActionManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLSequence;

public class YAMLHyphenTypedHandler extends TypedHandlerDelegate {
  @NotNull
  @Override
  public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    autoIndentHyphen(c, project, editor, file);
    return Result.CONTINUE;
  }

  private static void autoIndentHyphen(char c,
                                       @NotNull Project project,
                                       @NotNull Editor editor,
                                       @NotNull PsiFile file) {
    if (!(c == ' ' && file instanceof YAMLFile)) {
      return;
    }

    if (editor.getDocument().getCharsSequence().length() == 0) {
      // not sure it is possible
      return;
    }

    int curPosOffset = editor.getCaretModel().getOffset();
    if (curPosOffset < 2) {
      return;
    }

    int offset = curPosOffset - 2;
    if (editor.getDocument().getCharsSequence().charAt(offset) != '-') {
      return;
    }

    if (curPosOffset < editor.getDocument().getTextLength() && editor.getDocument().getCharsSequence().charAt(curPosOffset) != '\n') {
      //TODO: not sure this check is needed
      return;
    }

    //TODO: not sure it is needed!
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    PsiElement element = file.findElementAt(offset);
    if (PsiUtilCore.getElementType(element) != YAMLTokenTypes.SEQUENCE_MARKER) {
      return;
    }

    PsiElement item = element.getParent();
    if (PsiUtilCore.getElementType(item) != YAMLElementTypes.SEQUENCE_ITEM) {
      // Should not be possible now
      return;
    }

    PsiElement sequence = item.getParent();
    if (PsiUtilCore.getElementType(sequence) != YAMLElementTypes.SEQUENCE) {
      // It could be some composite component (with syntax error)
      return;
    }

    if (((YAMLSequence)sequence).getItems().size() != 1) {
      return;
    }

    // TODO: Could it be done without such cast magic?
    // TODO: Undo checkpoint will be stored even if no posformatting will be performed (looks like a bug)
    DefaultRawTypedHandler handler = ((EditorActionManagerImpl) EditorActionManager.getInstance()).getDefaultRawTypedHandler();
    handler.beginUndoablePostProcessing();

    ApplicationManager.getApplication().runWriteAction(() -> {
      // TODO: Should I catch IncorrectOperationException ? ( like in com/intellij/codeInsight/editorActions/TypedHandler.java:656 )
      int newOffset = CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
      editor.getCaretModel().moveToOffset(newOffset + 2);
      //editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE); // TODO: is it needed ?
      //editor.getSelectionModel().removeSelection(); // TODO: is it needed ?
    });
  }
}
