/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * Handles overtyping ':' in definitions.
 * User: dcheryasov
 * Date: May 29, 2009 4:42:03 AM
 */
public class PyKeywordTypedHandler extends TypedHandlerDelegate {
  @Override
  public Result beforeCharTyped(char character, Project project, Editor editor, PsiFile file, FileType fileType) {
    if (!(fileType instanceof PythonFileType)) return Result.CONTINUE; // else we'd mess up with other file types!
    if (character == ':') {
      final Document document = editor.getDocument();
      final int offset = editor.getCaretModel().getOffset();

      PsiElement token = file.findElementAt(offset - 1);
      if (token == null || offset >= document.getTextLength()) return Result.CONTINUE; // sanity check: beyond EOL

      PsiElement here_elt = file.findElementAt(offset);
      if (here_elt == null) return Result.CONTINUE; 
      if (here_elt instanceof PyStringLiteralExpression || here_elt.getParent() instanceof PyStringLiteralExpression) return Result.CONTINUE;

      // double colons aren't found in Python's syntax, so we can safely overtype a colon everywhere but strings.
      String here_text = here_elt.getText();
      if (":".equals(here_text)) {
        editor.getCaretModel().moveToOffset(offset + 1); // overtype, that is, jump over
        return Result.STOP;
      }
      PyUnindentingInsertHandler.unindentAsNeeded(project, editor, file);
    }

    return Result.CONTINUE; // the default
  }

}
