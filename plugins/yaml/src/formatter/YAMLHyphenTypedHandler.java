// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.formatter;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.impl.DefaultRawTypedHandler;
import com.intellij.openapi.editor.impl.TypedActionImpl;
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

import static org.jetbrains.yaml.settingsSync.YamlBackendExtensionSuppressorKt.shouldDoNothingInBackendMode;

public class YAMLHyphenTypedHandler extends TypedHandlerDelegate {
  @Override
  public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (shouldDoNothingInBackendMode()) return Result.CONTINUE;
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
    if (!file.isValid()) {
      return;
    }

    int curPosOffset = editor.getCaretModel().getOffset();
    if (curPosOffset < 2) {
      return;
    }

    int offset = curPosOffset - 2;
    Document document = editor.getDocument();

    if (document.getCharsSequence().charAt(offset) != '-') {
      return;
    }

    if (curPosOffset < document.getTextLength() && document.getCharsSequence().charAt(curPosOffset) != '\n') {
      return;
    }

    PsiDocumentManager.getInstance(project).commitDocument(document);

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

    DefaultRawTypedHandler handler = ((TypedActionImpl)TypedAction.getInstance()).getDefaultRawTypedHandler();
    handler.beginUndoablePostProcessing();

    ApplicationManager.getApplication().runWriteAction(() -> {
      int newOffset = CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
      editor.getCaretModel().moveToOffset(newOffset + 2);
    });
  }
}
