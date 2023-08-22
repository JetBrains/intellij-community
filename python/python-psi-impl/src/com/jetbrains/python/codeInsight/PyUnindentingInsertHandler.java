// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCoreUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.completion.PythonLookupElement;
import com.jetbrains.python.psi.PyStatementWithElse;
import com.jetbrains.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Adjusts indentation after a final part keyword is inserted, e.g. an "else:".
 */
public final class PyUnindentingInsertHandler implements InsertHandler<PythonLookupElement> {
  public final static PyUnindentingInsertHandler INSTANCE = new PyUnindentingInsertHandler();

  private PyUnindentingInsertHandler() {
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull PythonLookupElement item) {
    unindentAsNeeded(context.getProject(), context.getEditor(), context.getFile());
  }

  /**
   * Unindent current line to be flush with a starting part, detecting the part if necessary.
   *
   * @return true if unindenting succeeded
   */
  public static boolean unindentAsNeeded(Project project, Editor editor, PsiFile file) {
    // TODO: handle things other than "else"
    final Document document = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    CharSequence text = document.getCharsSequence();
    if (offset >= text.length()) {
      offset = text.length() - 1;
    }

    int line_start_offset = document.getLineStartOffset(document.getLineNumber(offset));
    int nonspace_offset = findBeginning(line_start_offset, text);


    Class<? extends PsiElement> parentClass = null;

    int last_offset = nonspace_offset + PyNames.FINALLY.length(); // the longest of all
    if (last_offset > offset) last_offset = offset;
    int local_length = last_offset - nonspace_offset + 1;
    if (local_length > 0) {
      String piece = text.subSequence(nonspace_offset, last_offset + 1).toString();
      final int else_len = PyNames.ELSE.length();
      if (local_length >= else_len) {
        if ((piece.startsWith(PyNames.ELSE) || piece.startsWith(PyNames.ELIF)) &&
            (else_len == piece.length() || piece.charAt(else_len) < 'a' || piece.charAt(else_len) > 'z')) {
          parentClass = PyStatementWithElse.class;
        }
      }
      final int except_len = PyNames.EXCEPT.length();
      if (local_length >= except_len) {
        if (piece.startsWith(PyNames.EXCEPT) &&
            (except_len == piece.length() || piece.charAt(except_len) < 'a' || piece.charAt(except_len) > 'z')) {
          parentClass = PyTryExceptStatement.class;
        }
      }
      final int finally_len = PyNames.FINALLY.length();
      if (local_length >= finally_len) {
        if (piece.startsWith(PyNames.FINALLY) &&
            (finally_len == piece.length() || piece.charAt(finally_len) < 'a' || piece.charAt(finally_len) > 'z')) {
          parentClass = PyTryExceptStatement.class;
        }
      }
    }


    if (parentClass == null) return false; // failed

    PsiDocumentManager.getInstance(project).commitDocument(document); // reparse

    PsiElement token = file.findElementAt(offset - 2); // -1 is our ':'; -2 is even safer.
    PsiElement outer = PsiTreeUtil.getParentOfType(token, parentClass);
    if (outer != null) {
      int outer_offset = outer.getTextOffset();
      int outer_indent = outer_offset - document.getLineStartOffset(document.getLineNumber(outer_offset));
      assert outer_indent >= 0;
      int current_indent = nonspace_offset - line_start_offset;
      int indent = outer_indent - current_indent;
      EditorCoreUtil.indentLine(project, editor, document.getLineNumber(offset), editor.getSettings().isUseTabCharacter(project)
                                                                                   ? indent * editor.getSettings().getTabSize(project)
                                                                                   : indent, false);
      //TODO!!!: shouldUseSmartTabs! is it applicable?
      return true;
    }
    return false;
  }


  // finds offset of first non-space in the line
  private static int findBeginning(int start_offset, CharSequence text) {
    int current_offset = start_offset;
    int text_length = text.length();
    while (current_offset < text_length) {
      char current_char = text.charAt(current_offset);
      if (current_char != ' ' && current_char != '\t' && current_char != '\n') break;
      current_offset += 1;
    }
    return current_offset;
  }
}
