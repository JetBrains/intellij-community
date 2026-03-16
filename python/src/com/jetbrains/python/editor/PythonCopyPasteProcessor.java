// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.editor;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User : catherine
 */
public final class PythonCopyPasteProcessor implements CopyPastePreProcessor {

  /**
   * Keywords that start multiline block statements
   */
  private static final Set<String> START_KEYWORDS = ImmutableSet.of("async",
                                                                    "def",
                                                                    "class",
                                                                    "with",
                                                                    "if", "elif", "else",
                                                                    "while", "for",
                                                                    "try", "except", "finally");

  @Override
  public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE || !isSupportedFile(file)) {
      return null;
    }
    // Expand copied text if it can cause indentation ambiguity
    
    // Text was selected with a single caret and might begin with a block statement 
    if (startOffsets.length == 1 && endOffsets.length == 1 && fragmentBeginsWithBlockStatement(text)) {
      final int start = startOffsets[0];
      final int end = endOffsets[0];

      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document != null) {
        final int startLine = document.getLineNumber(start);
        final int startLineOffset = getLineStartSafeOffset(document, startLine);
        if (start != startLineOffset && startLine != document.getLineNumber(end)) {
          final PsiElement keyword = file.findElementAt(start);
          if (keyword != null && START_KEYWORDS.contains(keyword.getText())) {
            final PyStatementListContainer block = PsiTreeUtil.getParentOfType(keyword, PyStatementListContainer.class);
            // Statement body is in selection
            if (block != null && end > block.getStatementList().getTextOffset()) {
              final String linePrefix = document.getText(TextRange.create(startLineOffset, start));
              if (StringUtil.isEmptyOrSpaces(linePrefix)) {
                return linePrefix + text;
              }
            }
          }
        }
      }
    }
    
    return null;
  }

  @Override
  public @NotNull String preprocessOnPaste(Project project,
                                           PsiFile file,
                                           Editor editor,
                                           String text,
                                           RawText rawText) {
    if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE || !isSupportedFile(file)) {
      return text;
    }

    final CaretModel caretModel = editor.getCaretModel();
    final SelectionModel selectionModel = editor.getSelectionModel();
    final Document document = editor.getDocument();
    final int caretOffset = selectionModel.getSelectionStart() != selectionModel.getSelectionEnd() ?
                            selectionModel.getSelectionStart() : caretModel.getOffset();
    final int lineNumber = document.getLineNumber(caretOffset);
    final int lineStartOffset = getLineStartSafeOffset(document, lineNumber);
    final int lineEndOffset = document.getLineEndOffset(lineNumber);

    final String line = document.getText(TextRange.create(lineStartOffset, lineEndOffset));
    
    final String linePrefix = document.getText(TextRange.create(lineStartOffset, caretOffset));
    if (!StringUtil.isEmptyOrSpaces(linePrefix)) return text;

    final PsiElement element = file.findElementAt(caretOffset);
    if (PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class) != null) return text;

    text = addLeadingSpacesToNormalizeSelection(file, text);
    final String fragmentIndent = PyIndentUtil.findCommonIndent(text, false);
    final String newIndent = inferBestIndent(file, document, caretOffset, lineNumber, fragmentIndent);
    String newText = PyIndentUtil.changeIndent(text, false, newIndent);

    if (!selectionModel.hasSelection() && shouldPasteOnPreviousLine(file, text, caretOffset)) {
      caretModel.moveToOffset(lineStartOffset);
      if (StringUtil.isEmptyOrSpaces(line)) {
        ApplicationManager.getApplication().runWriteAction(() -> document.deleteString(lineStartOffset, lineEndOffset));
      }
    }
    else {
      // Don't duplicate the existing whitespace prefix of the line
      newText = StringUtil.trimStart(newText, linePrefix);
    }

    final boolean useTabs = PyIndentUtil.areTabsUsedForIndentation(file);
    // TODO Combine shouldPasteOnPreviousLine() check with addLineBreak() as they really complement each other
    if (addLinebreak(text, line, useTabs) && selectionModel.getSelectionStart() == selectionModel.getSelectionEnd()) {
      newText += "\n";
    }
    return newText;
  }

  private static boolean isSupportedFile(PsiFile file) {
    return file instanceof PyFile;
  }

  private static @NotNull String addLeadingSpacesToNormalizeSelection(@NotNull PsiFile file, @NotNull String text) {
    if (!fragmentBeginsWithBlockStatement(text)) {
      return text;
    }

    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(file.getProject(), "dummy.py", text, false);
    //fragment.setContext(file);
    final PyStatementListContainer statement = as(fragment.getFirstChild(), PyStatementListContainer.class);
    if (statement == null) {
      return text;
    }

    final String statementIndent = PyIndentUtil.getElementIndent(statement);
    if (!statementIndent.isEmpty()) {
      return text;
    }
    
    final String indentStep = PyIndentUtil.getIndentFromSettings(file);
    final String bodyIndent = PyIndentUtil.getElementIndent(statement.getStatementList());
    final String expectedBodyIndent = statementIndent + indentStep;
    if (bodyIndent.startsWith(expectedBodyIndent)) {
      return bodyIndent.substring(0, bodyIndent.length() - indentStep.length()) + text;
    }
    return text;
  }

  private static boolean fragmentBeginsWithBlockStatement(@NotNull String text) {
    return ContainerUtil.exists(START_KEYWORDS, keyword -> text.startsWith(keyword + " ") || text.startsWith(keyword + ":"));
  }

  private static @NotNull String inferBestIndent(@NotNull PsiFile file,
                                                 @NotNull Document document,
                                                 int caretOffset,
                                                 int lineNumber,
                                                 @NotNull String fragmentIndent) {

    PsiElement nonWS = PyUtil.findNextAtOffset(file, caretOffset, PsiWhiteSpace.class);
    if (nonWS != null) {
      final IElementType nonWSType = nonWS.getNode().getElementType();
      if (nonWSType == PyTokenTypes.ELSE_KEYWORD || nonWSType == PyTokenTypes.ELIF_KEYWORD ||
          nonWSType == PyTokenTypes.EXCEPT_KEYWORD || nonWSType == PyTokenTypes.FINALLY_KEYWORD) {
        lineNumber -= 1;
        nonWS = PyUtil.findNextAtOffset(file, getLineStartSafeOffset(document, lineNumber), PsiWhiteSpace.class);
      }
    }

    if (nonWS != null && document.getLineNumber(nonWS.getTextOffset()) == lineNumber) {
      return PyIndentUtil.getLineIndent(document, lineNumber);
    }

    final int lineStartOffset = getLineStartSafeOffset(document, lineNumber);
    final PsiElement ws = file.findElementAt(lineStartOffset);
    final String userIndent = document.getText(TextRange.create(lineStartOffset, caretOffset));
    
    final PyStatementList statementList = findEmptyStatementListNearby(file, lineStartOffset);
    if (statementList != null) {
      return PyIndentUtil.getElementIndent(statementList);
    }

    final String smallestIndent = ws == null? "" : PyIndentUtil.getElementIndent(ws);
    final PyStatementListContainer parentBlock = PsiTreeUtil.getParentOfType(ws, PyStatementListContainer.class);
    final PyStatementListContainer deepestBlock = getDeepestPossibleParentBlock(file, caretOffset);
    final String greatestIndent;
    if (deepestBlock != null && (parentBlock == null || PsiTreeUtil.isAncestor(parentBlock, deepestBlock, true))) {
      greatestIndent = PyIndentUtil.getElementIndent(deepestBlock.getStatementList());
    }
    else {
      greatestIndent = smallestIndent;
    }
    if (caretOffset == lineStartOffset && fragmentIndent.startsWith(smallestIndent) && greatestIndent.startsWith(fragmentIndent)) {
      return fragmentIndent;
    }
    if (smallestIndent.startsWith(userIndent)) {
      return smallestIndent;
    }
    if (userIndent.startsWith(greatestIndent)) {
      return greatestIndent;
    }
    return userIndent;
  }

  private static @Nullable PyStatementList findEmptyStatementListNearby(@NotNull PsiFile file, int offset) {
    final PsiWhiteSpace whitespace = findWhitespaceAtCaret(file, offset);
    if (whitespace == null) {
      return null;
    }
    
    PyStatementList statementList = ObjectUtils.chooseNotNull(as(whitespace.getNextSibling(), PyStatementList.class),
                                                              as(whitespace.getPrevSibling(), PyStatementList.class));
    if (statementList == null) {
      final PsiElement prevLeaf = getPrevNonCommentLeaf(whitespace);
      if (prevLeaf instanceof PsiErrorElement) {
        statementList = as(prevLeaf.getParent(), PyStatementList.class);
      }
    }
    return statementList != null && statementList.getStatements().length == 0 ? statementList : null;
  }

  private static @Nullable PsiWhiteSpace findWhitespaceAtCaret(@NotNull PsiFile file, int offset) {
    return as(file.findElementAt(offset == file.getTextLength() && offset > 0 ? offset - 1 : offset), PsiWhiteSpace.class);
  }

  private static @Nullable PyStatementListContainer getDeepestPossibleParentBlock(@NotNull PsiFile file, int offset) {
    final PsiWhiteSpace whitespace = findWhitespaceAtCaret(file, offset);
    if (whitespace == null) {
      return null;
    }
    final PsiElement prevLeaf = getPrevNonCommentLeaf(whitespace);
    return PsiTreeUtil.getParentOfType(prevLeaf, PyStatementListContainer.class);
  }

  private static boolean shouldPasteOnPreviousLine(final @NotNull PsiFile file, @NotNull String text, int caretOffset) {
    final boolean useTabs = PyIndentUtil.areTabsUsedForIndentation(file);
    final PsiElement nonWS = PyUtil.findNextAtOffset(file, caretOffset, PsiWhiteSpace.class);
    if (nonWS == null || text.endsWith("\n")) {
      return true;
    }
    if (inStatementList(file, caretOffset) && (text.startsWith(useTabs ? "\t" : " ") || StringUtil.split(text, "\n").size() > 1)) {
      return true;
    }
    return false;
  }

  private static @Nullable PsiElement getPrevNonCommentLeaf(@NotNull PsiElement element) {
    PsiElement anchor = PsiTreeUtil.prevLeaf(element);
    while (anchor instanceof PsiComment || anchor instanceof PsiWhiteSpace) {
      anchor = PsiTreeUtil.prevLeaf(anchor, false);
    }
    return anchor;
  }

  private static boolean inStatementList(final @NotNull PsiFile file, int caretOffset) {
    final PsiElement element = file.findElementAt(caretOffset);
    return PsiTreeUtil.getParentOfType(element, PyStatementListContainer.class) != null;
  }

  private static boolean addLinebreak(@NotNull String text, @NotNull String line, boolean useTabs) {
    return (text.startsWith(useTabs ? "\t" : " ") || StringUtil.split(text, "\n").size() > 1)
           && !text.endsWith("\n") && !StringUtil.isEmptyOrSpaces(line);
  }

  private static int getLineStartSafeOffset(final Document document, int line) {
    if (line >= document.getLineCount()) return document.getTextLength();
    if (line < 0) return 0;
    return document.getLineStartOffset(line);
  }

}
