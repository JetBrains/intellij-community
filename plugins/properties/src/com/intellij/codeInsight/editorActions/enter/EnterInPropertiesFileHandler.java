/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.enter;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import org.jetbrains.annotations.NotNull;

public class EnterInPropertiesFileHandler extends EnterHandlerDelegateAdapter {
  public Result preprocessEnter(@NotNull final PsiFile file, @NotNull final Editor editor, @NotNull final Ref<Integer> caretOffsetRef, @NotNull final Ref<Integer> caretAdvance,
                                @NotNull final DataContext dataContext, final EditorActionHandler originalHandler) {
    int caretOffset = caretOffsetRef.get().intValue();
    PsiElement psiAtOffset = file.findElementAt(caretOffset);
    if (file instanceof PropertiesFile) {
      handleEnterInPropertiesFile(editor, editor.getDocument(), psiAtOffset, caretOffset);
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
        toInsert = "\\\n  ";
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
