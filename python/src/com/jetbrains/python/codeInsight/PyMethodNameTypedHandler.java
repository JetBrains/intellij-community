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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;

/**
 * Adds appropriate first parameter to a freshly-typed method declaration.
 * <br/>
 * User: dcheryasov
 * Date: 11/29/10 12:44 AM
 */
public class PyMethodNameTypedHandler extends TypedHandlerDelegate {
  @Override
  public Result beforeCharTyped(char character, Project project, Editor editor, PsiFile file, FileType fileType) {
    if (DumbService.isDumb(project) || !(fileType instanceof PythonFileType)) return Result.CONTINUE; // else we'd mess up with other file types!
    if (character == '(') {
      if (!PyCodeInsightSettings.getInstance().INSERT_SELF_FOR_METHODS) {
        return Result.CONTINUE;
      }
      final Document document = editor.getDocument();
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      final int offset = editor.getCaretModel().getOffset();

      PsiElement token = file.findElementAt(offset - 1);
      if (token == null) return Result.CONTINUE; // sanity check: beyond EOL

      final ASTNode token_node = token.getNode();
      if (token_node != null && token_node.getElementType() == PyTokenTypes.IDENTIFIER) {
        PsiElement maybe_def = PyPsiUtils.getPrevNonCommentSibling(token.getPrevSibling(), false);
        if (maybe_def != null) {
          ASTNode def_node = maybe_def.getNode();
          if (def_node != null && def_node.getElementType() == PyTokenTypes.DEF_KEYWORD) {
            PsiElement maybe_func = token.getParent();
            if (maybe_func instanceof PyFunction) {
              PyFunction func = (PyFunction)maybe_func;
              PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(func);
              if (flags != null) {
                // we're in a method
                // TODO: all string constants go to Settings
                String pname = flags.isClassMethod() || flags.isMetaclassMethod() ? "cls" : "self";
                final boolean is_new = PyNames.NEW.equals(func.getName());
                if (flags.isMetaclassMethod() && is_new) {
                  pname = "typ";
                }
                else if (flags.isClassMethod() || is_new) {
                  pname = "cls";
                }
                else if (flags.isStaticMethod()) pname = "";
                documentManager.commitDocument(document);
                // TODO: only print the ")" if Settings require it
                int caretOffset = editor.getCaretModel().getOffset();
                String textToType = "(" + pname + ")";
                CharSequence chars = editor.getDocument().getCharsSequence();
                if (caretOffset == chars.length() || chars.charAt(caretOffset) != ':') {
                  textToType += ':';
                }
                EditorModificationUtil.insertStringAtCaret(editor, textToType, true, 1 + pname.length()); // right after param name
                return Result.STOP;
              }
            }
          }
        }
      }
    }
    return Result.CONTINUE; // the default
  }
}
