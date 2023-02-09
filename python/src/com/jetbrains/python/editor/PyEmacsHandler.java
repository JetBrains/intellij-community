// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.emacs.EmacsProcessingHandler;
import com.intellij.formatting.IndentInfo;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PySequenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Python-specific Emacs-like processing extension.
 * <p/>
 * Thread-safe.
 */
public class PyEmacsHandler implements EmacsProcessingHandler {

  private static final TokenSet COMPOUND_STATEMENT_TYPES = TokenSet.create(
    PyElementTypes.IF_STATEMENT, PyTokenTypes.IF_KEYWORD, PyTokenTypes.ELIF_KEYWORD, PyTokenTypes.ELSE_KEYWORD,
    PyElementTypes.WHILE_STATEMENT, PyTokenTypes.WHILE_KEYWORD,
    PyElementTypes.FOR_STATEMENT, PyTokenTypes.FOR_KEYWORD,
    PyElementTypes.WITH_STATEMENT, PyTokenTypes.WITH_KEYWORD,
    PyElementTypes.TRY_EXCEPT_STATEMENT, PyTokenTypes.TRY_KEYWORD, PyTokenTypes.EXCEPT_KEYWORD, PyTokenTypes.FINALLY_KEYWORD,
    PyElementTypes.FUNCTION_DECLARATION, PyTokenTypes.DEF_KEYWORD,
    PyElementTypes.CLASS_DECLARATION, PyTokenTypes.CLASS_KEYWORD
  );

  private enum ProcessingResult {
    /** Particular sub-routine did the job and no additional processing is necessary. */
    STOP_SUCCESSFUL,

    /** Particular sub-routine detected that the processing should be stopped. */
    STOP_UNSUCCESSFUL,

    /** Processing should be continued */
    CONTINUE
  }

  /**
   * Tries to make active line(s) belong to another code block by changing their indentation.
   *
   * @param project     current project
   * @param editor      current editor
   * @param file        current file
   * @return            {@link Result#STOP} if indentation level is changed and further processing should be stopped;
   *                    {@link Result#CONTINUE} otherwise
   */
  @NotNull
  @Override
  public Result changeIndent(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    // The algorithm is as follows:
    //     1. Check if the editor has selection. Do nothing then as Emacs behaves so;
    //     2. Indent current line one level right if possible;
    //     3. Indent current line to the left-most position if possible;

    SelectionModel selectionModel = editor.getSelectionModel();
    // Emacs Tab doesn't adjust indent in case of active selection. So do we.
    if (selectionModel.hasSelection()) {
      return Result.CONTINUE;
    }

    // Check if current line is empty. Return eagerly then.
    Document document = editor.getDocument();
    int caretOffset = editor.getCaretModel().getOffset();
    int caretLine = document.getLineNumber(caretOffset);
    if (DocumentUtil.isLineEmpty(document, caretLine)) {
      return Result.CONTINUE;
    }

    ChangeIndentContext context = new ChangeIndentContext(project, file, editor, document, caretLine);
    int targetLineIndent = getLineIndent(context, context.targetLine);
    int soleLineIndent = getSoleIndent(context);
    int lineStart = context.document.getLineStartOffset(context.targetLine);
    if (caretOffset - lineStart < targetLineIndent) {
      changeIndent(context, soleLineIndent);
      return Result.STOP;
    }
    switch (tryToIndentToRight(context)) {
      case STOP_SUCCESSFUL: return Result.STOP;
      case STOP_UNSUCCESSFUL: return Result.CONTINUE;
      case CONTINUE: break;
    }

    if (tryToIndentToLeft(context)) {
      return Result.STOP;
    }

    return Result.CONTINUE;
  }

  private static int getSoleIndent(@NotNull ChangeIndentContext context) {
    PsiElement element = context.file.findElementAt(context.editor.getCaretModel().getOffset());

    int prevLine = context.targetLine - 1;
    while (prevLine >= 0 && DocumentUtil.isLineEmpty(context.document, prevLine)) {
      prevLine--;
    }

    if (prevLine < 0) {
      return -1;
    }

    int indent = getLineIndent(context, prevLine);
    int newIndent = -1;
    if (isLineStartsWithCompoundStatement(context, prevLine)) {
      newIndent = indent + context.getIndentOptions().INDENT_SIZE;
    }
    else if (PsiTreeUtil.getParentOfType(element, PySequenceExpression.class) != null && indent == 0) {
      newIndent = context.getIndentOptions().INDENT_SIZE;
    }
    else {
      if (indent < getLineIndent(context, context.targetLine)) {
        newIndent = indent;
      }
    }
    return newIndent;
  }

  /**
   * Tries to indent active line to the right.
   *
   * @param context    current processing context
   * @return           processing result
   */
  private static ProcessingResult tryToIndentToRight(@NotNull ChangeIndentContext context) {
    int targetLineIndent = getLineIndent(context, context.targetLine);
    List<LineInfo> lineInfos = collectIndentsGreaterOrEqualToCurrent(context, context.targetLine);

    int newIndent = -1;
    for (int i = lineInfos.size() - 1; i >= 0; i--) {
      LineInfo lineInfo = lineInfos.get(i);
      if (lineInfo.indent == targetLineIndent && !lineInfo.startsWithCompoundStatement) {
        continue;
      }
      newIndent = lineInfo.indent;
      if (lineInfo.startsWithCompoundStatement) {
        newIndent += context.getIndentOptions().INDENT_SIZE;
      }
      break;
    }

    if (newIndent == targetLineIndent || newIndent < 0) {
      return ProcessingResult.CONTINUE;
    }

    changeIndent(context, newIndent);
    return ProcessingResult.STOP_SUCCESSFUL;
  }

  private static boolean tryToIndentToLeft(@NotNull ChangeIndentContext context) {
    if (context.targetLine == 0 || !containsNonWhiteSpaceData(context.document, 0, context.targetLine)) {
      changeIndent(context, 0);
      return false;
    }

    int newIndent = -1;
    for (int line = 0; line < context.targetLine; line++) {
      if (DocumentUtil.isLineEmpty(context.document, line)) {
        continue;
      }
      int indent = getLineIndent(context, line);
      if (isLineStartsWithCompoundStatement(context, line)) {
        newIndent = indent;
        break;
      }
      else if (newIndent < 0) {
        newIndent = indent;
      }
    }

    if (newIndent < 0) {
      return false;
    }

    changeIndent(context, newIndent);
    return true;
  }

  private static void changeIndent(@NotNull ChangeIndentContext context, int newIndent) {
    int caretOffset = context.editor.getCaretModel().getOffset();
    String newIndentString = new IndentInfo(0, newIndent, 0).generateNewWhiteSpace(context.getIndentOptions());
    int start = context.document.getLineStartOffset(context.targetLine);
    int end = DocumentUtil.getFirstNonSpaceCharOffset(context.document, context.targetLine);
    context.editor.getDocument().replaceString(start, end, newIndentString);
    if (caretOffset >= start && caretOffset < end) {
      context.editor.getCaretModel().moveToOffset(start + newIndentString.length());
    }
  }

  private static boolean containsNonWhiteSpaceData(@NotNull Document document, int startLine, int endLine) {
    final int start = document.getLineStartOffset(startLine);
    final int end = document.getLineStartOffset(endLine);
    for (int i = start; i < end; i++) {
      if (!DocumentUtil.isLineEmpty(document, i)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Inspects lines that are located before the given target line and collects information about lines which indents are greater
   * or equal to the target line indent.
   *
   * @param context       current processing context
   * @param targetLine    target line
   * @return              list of lines located before the target and that start new indented blocks. Note that they are
   *                      stored by their indent size in ascending order
   */
  private static List<LineInfo> collectIndentsGreaterOrEqualToCurrent(@NotNull ChangeIndentContext context, int targetLine) {
    List<LineInfo> result = new ArrayList<>();
    int targetLineIndent = getLineIndent(context, targetLine);
    final int soleIndent = getSoleIndent(context);
    if (soleIndent > 0) {
      result.add(new LineInfo(targetLine, soleIndent, false));
    }
    else {
      result.add(new LineInfo(targetLine, targetLineIndent, false));
    }
    int indentUsedLastTime = Integer.MAX_VALUE;
    for (int i = targetLine - 1; i >= 0 && indentUsedLastTime > targetLineIndent; i--) {
      if (DocumentUtil.isLineEmpty(context.document, i)) {
        continue;
      }
      int indent = getLineIndent(context, i);
      if (indent < targetLineIndent) {
        break;
      }
      if (indent >= indentUsedLastTime) {
        continue;
      }
      PsiElement element = context.file.findElementAt(context.document.getLineStartOffset(i) + indent);
      if (element == null) {
        continue;
      }
      ASTNode node = element.getNode();
      result.add(new LineInfo(i, indent, COMPOUND_STATEMENT_TYPES.contains(node.getElementType())));
      indentUsedLastTime = indent;
    }
    return result;
  }

  private static boolean isLineStartsWithCompoundStatement(@NotNull ChangeIndentContext context, int line) {
    PsiElement element = context.file.findElementAt(context.document.getLineStartOffset(line) + getLineIndent(context, line));
    if (element == null) {
      return false;
    }
    ASTNode node = element.getNode();
    if (node == null) {
      return false;
    }
    return COMPOUND_STATEMENT_TYPES.contains(node.getElementType());
  }

  private static int getLineIndent(@NotNull ChangeIndentContext context, int line) {
    int start = context.document.getLineStartOffset(line);
    int end = context.document.getLineEndOffset(line);
    int result = 0;
    CharSequence text = context.document.getCharsSequence();
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      switch (c) {
        case ' ' -> result++;
        case '\t' -> result += context.getIndentOptions().TAB_SIZE;
        default -> {
          return result;
        }
      }
    }
    return result;
  }

  private static class LineInfo {

    public final int     line;
    public final int     indent;
    public final boolean startsWithCompoundStatement;

    LineInfo(int line, int indent, boolean startsWithCompoundStatement) {
      this.line = line;
      this.indent = indent;
      this.startsWithCompoundStatement = startsWithCompoundStatement;
    }

    @Override
    public String toString() {
      return "line=" + line + ", indent=" + indent + ", compound=" + startsWithCompoundStatement;
    }
  }

  private static final class ChangeIndentContext {
    @NotNull public final Project  project;
    @NotNull public final PsiFile  file;
    @NotNull public final Editor   editor;
    @NotNull public final Document document;
    public final          int      targetLine;

    private CommonCodeStyleSettings.IndentOptions myIndentOptions;

    private ChangeIndentContext(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor,
                                @NotNull Document document, int targetLine)
    {
      this.project = project;
      this.file = file;
      this.editor = editor;
      this.document = document;
      this.targetLine = targetLine;
    }

    public CommonCodeStyleSettings.IndentOptions getIndentOptions() {
      if (myIndentOptions == null) {
        myIndentOptions = CodeStyle.getIndentOptions(file);
      }

      return myIndentOptions;
    }
  }
}
