// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

final class EnterInPropertiesFileHandler extends EnterHandlerDelegateAdapter {
  @Override
  public Result preprocessEnter(@NotNull final PsiFile file, @NotNull final Editor editor, @NotNull final Ref<Integer> caretOffsetRef, @NotNull final Ref<Integer> caretAdvance,
                                @NotNull final DataContext dataContext, final EditorActionHandler originalHandler) {
    if (file instanceof PropertiesFile) {
      int caretOffset = caretOffsetRef.get().intValue();
      Document document = editor.getDocument();
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
      PsiElement psiAtOffset = file.findElementAt(caretOffset);
      handleEnterInPropertiesFile(editor, document, psiAtOffset, caretOffset);
      return Result.Stop;
    }
    return Result.Continue;
  }

  private static void handleEnterInPropertiesFile(final Editor editor,
                                                  final Document document,
                                                  final PsiElement psiAtOffset,
                                                  int caretOffset) {
    String text = document.getText();
    String line = text.substring(0, caretOffset);
    int i = line.lastIndexOf('\n');
    if (i > 0) {
      line = line.substring(i);
    }
    final String toInsert;
    if (PropertiesUtil.isUnescapedBackSlashAtTheEnd(line)) {
      toInsert = "\n  ";
    }
    else {
      final IElementType elementType = psiAtOffset == null ? null : psiAtOffset.getNode().getElementType();

      if (elementType == PropertiesTokenTypes.VALUE_CHARACTERS) {
        if (text.charAt(caretOffset) == ' ' || text.charAt(caretOffset) == '\t') {
          // escape the whitespace on the next line like "\ "
          toInsert = "\\\n  \\";
        }
        else {
          toInsert = "\\\n  ";
        }
      }
      else if (elementType == PropertiesTokenTypes.END_OF_LINE_COMMENT && "#!".indexOf(document.getText().charAt(caretOffset)) == -1) {
        toInsert = "\n#";
      }
      else {
        toInsert = "\n";
      }
    }
    document.insertString(caretOffset, toInsert);
    caretOffset+=toInsert.length();
    editor.getCaretModel().moveToOffset(caretOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

}
