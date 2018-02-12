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

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Adds appropriate first parameter to a freshly-typed method declaration.
 * <br/>
 * User: dcheryasov
 */
public class PyMethodNameTypedHandler extends TypedHandlerDelegate {
  private static final Pattern DEF_THEN_IDENTIFIER = Pattern.compile(".*\\bdef\\s+" + PyNames.IDENTIFIER_RE);

  @NotNull
  @Override
  public Result beforeCharTyped(char character, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    if (DumbService.isDumb(project) || !(fileType instanceof PythonFileType)) return Result.CONTINUE; // else we'd mess up with other file types!
    if (character == '(') {
      if (!PyCodeInsightSettings.getInstance().INSERT_SELF_FOR_METHODS) {
        return Result.CONTINUE;
      }
      final Document document = editor.getDocument();
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      final int offset = editor.getCaretModel().getOffset();
      final int lineNumber = document.getLineNumber(offset);

      final String linePrefix = document.getText(TextRange.create(document.getLineStartOffset(lineNumber), offset));
      if (!DEF_THEN_IDENTIFIER.matcher(linePrefix).matches()) {
        return Result.CONTINUE;
      }
      documentManager.commitDocument(document);

      final PsiElement token = file.findElementAt(offset - 1);
      if (token == null) return Result.CONTINUE; // sanity check: beyond EOL

      final ASTNode tokenNode = token.getNode();
      if (tokenNode != null && tokenNode.getElementType() == PyTokenTypes.IDENTIFIER) {
        final PsiElement maybeDef = PyPsiUtils.getPrevNonCommentSibling(token.getPrevSibling(), false);
        if (maybeDef != null) {
          final ASTNode defNode = maybeDef.getNode();
          if (defNode != null && defNode.getElementType() == PyTokenTypes.DEF_KEYWORD) {
            final PsiElement maybeFunc = token.getParent();
            if (maybeFunc instanceof PyFunction) {
              final PyFunction func = (PyFunction)maybeFunc;
              final PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(func);
              if (flags != null) {
                // we're in a method
                // TODO: all string constants go to Settings
                String paramName = flags.isClassMethod() || flags.isMetaclassMethod() ? "cls" : "self";
                final boolean isNew = PyNames.NEW.equals(func.getName());
                if (flags.isMetaclassMethod() && isNew) {
                  paramName = "typ";
                }
                else if (flags.isClassMethod() || isNew) {
                  paramName = "cls";
                }
                else if (flags.isStaticMethod()) {
                  paramName = "";
                }
                // TODO: only print the ")" if Settings require it
                final CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(file, PythonLanguage.getInstance());
                final StringBuilder textToType = new StringBuilder();
                textToType.append("(");
                if (!paramName.isEmpty()) {
                  if (settings.SPACE_WITHIN_METHOD_PARENTHESES) {
                    textToType.append(" ");
                  }
                  textToType.append(paramName);
                  if (settings.SPACE_WITHIN_METHOD_PARENTHESES) {
                    textToType.append(" ");
                  }
                }
                else if (settings.SPACE_WITHIN_EMPTY_METHOD_PARENTHESES) {
                  textToType.append(" ");
                }
                textToType.append(")");
                final int caretOffset = editor.getCaretModel().getOffset();
                final CharSequence chars = editor.getDocument().getCharsSequence();
                if (caretOffset == chars.length() || chars.charAt(caretOffset) != ':') {
                  textToType.append(':');
                }
                EditorModificationUtil.insertStringAtCaret(editor, textToType.toString(), true, 1 + paramName.length()); // right after param name
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
