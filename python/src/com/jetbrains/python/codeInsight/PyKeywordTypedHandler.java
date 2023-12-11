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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles overtyping ':' in definitions.
 */
public final class PyKeywordTypedHandler extends TypedHandlerDelegate {
  @NotNull
  @Override
  public Result beforeCharTyped(char character, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    if (!(fileType instanceof PythonFileType)) return Result.CONTINUE; // else we'd mess up with other file types!
    if (character == ':') {
      final Document document = editor.getDocument();
      final int offset = editor.getCaretModel().getOffset();
      if (offset < document.getTextLength() && document.getCharsSequence().charAt(offset) == ':') {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        Result res = getOverTypeResult(editor, file);
        if (res == Result.STOP) {
          return res;
        }
      }
      PyUnindentingInsertHandler.unindentAsNeeded(project, editor, file);
    }
    return Result.CONTINUE; // the default
  }

  @Nullable
  private static Result getOverTypeResult(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();

    PsiElement token = file.findElementAt(offset - 1);
    if (token == null) return Result.CONTINUE; // sanity check: beyond EOL

    PsiElement elem = file.findElementAt(offset);
    if (elem == null) return Result.CONTINUE;

    if (elem.getNode().getElementType() == PyTokenTypes.COLON) {
      editor.getCaretModel().moveToOffset(offset + 1); // overtype, that is, jump over
      return Result.STOP;
    }
    return null;
  }
}
