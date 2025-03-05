// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.StaticSymbolWhiteSpaceDefinitionStrategy;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.editor.PyEditorHandlerConfig;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyWhiteSpaceFormattingStrategy extends StaticSymbolWhiteSpaceDefinitionStrategy {
  public PyWhiteSpaceFormattingStrategy() {
    super('\\');
  }

  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                  @NotNull PsiElement startElement,
                                                  int startOffset,
                                                  int endOffset,
                                                  CodeStyleSettings codeStyleSettings) {
    CharSequence whiteSpace =  super.adjustWhiteSpaceIfNecessary(whiteSpaceText, startElement, startOffset, endOffset, codeStyleSettings);
    if (!whiteSpace.isEmpty() && whiteSpace.charAt(0) == '\n' && !Strings.contains(whiteSpace, 0, whiteSpace.length(), '\\') &&
        needInsertBackslash(startElement.getContainingFile(), startOffset, false)) {
      return addBackslashPrefix(whiteSpace, codeStyleSettings);
    }
    return whiteSpace;
  }

  private static String addBackslashPrefix(CharSequence whiteSpace, CodeStyleSettings settings) {
    PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);
    return (pySettings.SPACE_BEFORE_BACKSLASH ? " \\" : "\\") + whiteSpace.toString();
  }

  /**
   * Python uses backslashes at the end of the line as indication that next line is an extension of the current one.
   * <p/>
   * Hence, we need to preserve them during white space manipulation.
   *
   *
   * @param whiteSpaceText    white space text to use by default for replacing sub-sequence of the given text
   * @param text              target text which region is to be replaced by the given white space symbols
   * @param startOffset       start offset to use with the given text (inclusive)
   * @param endOffset         end offset to use with the given text (exclusive)
   * @param codeStyleSettings the code style settings
   * @return                  symbols to use for replacing {@code [startOffset; endOffset)} sub-sequence of the given text
   */
  @Override
  public @NotNull CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                  @NotNull CharSequence text,
                                                  int startOffset,
                                                  int endOffset,
                                                  CodeStyleSettings codeStyleSettings, ASTNode nodeAfter) {
    // the general idea is that '\' symbol before line feed should be preserved
    Int2IntMap initialBackSlashes = countBackSlashes(text, startOffset, endOffset);
    if (initialBackSlashes.isEmpty()) {
      if (nodeAfter != null && !whiteSpaceText.isEmpty() && whiteSpaceText.charAt(0) == '\n' &&
          needInsertBackslash(nodeAfter, false)) {
        return addBackslashPrefix(whiteSpaceText, codeStyleSettings);
      }
      return whiteSpaceText;
    }

    Int2IntMap newBackSlashes = countBackSlashes(whiteSpaceText, 0, whiteSpaceText.length());
    boolean continueProcessing = false;
    IntIterator iterator = initialBackSlashes.keySet().iterator();
    while (iterator.hasNext()) {
      if (!newBackSlashes.containsKey(iterator.nextInt())) {
        continueProcessing = true;
        break;
      }
    }
    if (!continueProcessing) {
      return whiteSpaceText;
    }

    PyCodeStyleSettings settings = codeStyleSettings.getCustomSettings(PyCodeStyleSettings.class);
    StringBuilder result = new StringBuilder();
    int line = 0;
    for (int i = 0; i < whiteSpaceText.length(); i++) {
      char c = whiteSpaceText.charAt(i);
      if (c != '\n') {
        result.append(c);
        continue;
      }
      if (!newBackSlashes.containsKey(line++)) {
        if ((i == 0 || whiteSpaceText.charAt(i - 1) != ' ') && settings.SPACE_BEFORE_BACKSLASH) {
          result.append(' ');
        }
        result.append('\\');
      }
      result.append(c);
    }
    return result;
  }

  /**
   * Counts number of back slashes per-line.
   *
   * @param text      target text
   * @param start     start offset to use with the given text (inclusive)
   * @param end       end offset to use with the given text (exclusive)
   * @return          map that holds '{@code line number -> number of back slashes}' mapping for the target text
   */
  static @NotNull Int2IntMap countBackSlashes(CharSequence text, int start, int end) {
    Int2IntMap result=new Int2IntOpenHashMap();
    int line = 0;
    if (end > text.length()) {
      end = text.length();
    }
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\n' -> line++;
        case '\\' -> result.put(line, 1);
      }
    }
    return result;
  }

  public static boolean needInsertBackslash(PsiFile file, int offset, boolean autoWrapInProgress) {
    if (offset > 0) {
      final PsiElement beforeCaret = file.findElementAt(offset - 1);
      if (beforeCaret instanceof PsiWhiteSpace && beforeCaret.getText().indexOf('\\') >= 0) {
        // we've got a backslash at EOL already, don't need another one
        return false;
      }
    }
    PsiElement atCaret = file.findElementAt(offset);
    if (atCaret == null) {
      return false;
    }
    ASTNode nodeAtCaret = atCaret.getNode();
    return needInsertBackslash(nodeAtCaret, autoWrapInProgress);
  }

  private static boolean needInsertBackslash(ASTNode nodeAtCaret, boolean autoWrapInProgress) {
    if (PsiTreeUtil.getParentOfType(nodeAtCaret.getPsi(), PyAstFStringFragment.class) != null) {
      return false;
    }

    PsiElement statementBefore = findStatementBeforeCaret(nodeAtCaret);
    PsiElement statementAfter = findStatementAfterCaret(nodeAtCaret);
    if (statementBefore != statementAfter) {  // Enter pressed at statement break
      return false;
    }
    if (statementBefore == null) {  // empty file
      return false;
    }

    if (PsiTreeUtil.hasErrorElements(statementBefore)) {
      if (!autoWrapInProgress) {
        // code is already bad, don't mess it up even further
        return false;
      }
      // if we're in middle of typing, it's expected that we will have error elements
    }

    final int offset = nodeAtCaret.getTextRange().getStartOffset();
    if (inFromImportParentheses(statementBefore, offset)
        || inWithItemsParentheses(statementBefore, offset)
        || inCaseClauseParentheses(statementBefore, offset)) {
      return false;
    }

    PsiElement wrappableBefore = findWrappable(nodeAtCaret, true);
    PsiElement wrappableAfter = findWrappable(nodeAtCaret, false);
    if (!(wrappableBefore instanceof PsiComment)) {
      while (wrappableBefore != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableBefore, PyEditorHandlerConfig.WRAPPABLE_CLASSES);
        if (next == null) {
          break;
        }
        wrappableBefore = next;
      }
    }
    if (!(wrappableAfter instanceof PsiComment)) {
      while (wrappableAfter != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableAfter, PyEditorHandlerConfig.WRAPPABLE_CLASSES);
        if (next == null) {
          break;
        }
        wrappableAfter = next;
      }
    }
    if (wrappableBefore instanceof PsiComment || wrappableAfter instanceof PsiComment) {
      return false;
    }
    if (wrappableAfter == null) {
      return !(wrappableBefore instanceof PyAstDecoratorList);
    }
    return wrappableBefore != wrappableAfter;
  }

  private static @Nullable PsiElement findWrappable(ASTNode nodeAtCaret, boolean before) {
    PsiElement wrappable = before
                                 ? findBeforeCaret(nodeAtCaret, PyEditorHandlerConfig.WRAPPABLE_CLASSES)
                                 : findAfterCaret(nodeAtCaret, PyEditorHandlerConfig.WRAPPABLE_CLASSES);
    if (wrappable == null) {
      PsiElement emptyTuple = before
                              ? findBeforeCaret(nodeAtCaret, PyAstTupleExpression.class)
                              : findAfterCaret(nodeAtCaret, PyAstTupleExpression.class);
      if (emptyTuple != null && emptyTuple.getNode().getFirstChildNode().getElementType() == PyTokenTypes.LPAR) {
        wrappable = emptyTuple;
      }
    }
    return wrappable;
  }

  private static @Nullable PsiElement findStatementBeforeCaret(ASTNode node) {
    return findBeforeCaret(node, PyAstStatement.class, PyAstStatementPart.class);
  }

  private static @Nullable PsiElement findStatementAfterCaret(ASTNode node) {
    return findAfterCaret(node, PyAstStatement.class, PyAstStatementPart.class);
  }

  private static PsiElement findBeforeCaret(ASTNode atCaret, Class<? extends PsiElement>... classes) {
    while (atCaret != null) {
      atCaret = TreeUtil.prevLeaf(atCaret);
      if (atCaret != null && atCaret.getElementType() != TokenType.WHITE_SPACE) {
        return getNonStrictParentOfType(atCaret.getPsi(), classes);
      }
    }
    return null;
  }

  private static PsiElement findAfterCaret(ASTNode atCaret, Class<? extends PsiElement>... classes) {
    while (atCaret != null) {
      if (atCaret.getElementType() != TokenType.WHITE_SPACE) {
        return getNonStrictParentOfType(atCaret.getPsi(), classes);
      }
      atCaret = TreeUtil.nextLeaf(atCaret);
    }
    return null;
  }

  private static @Nullable <T extends PsiElement> T getNonStrictParentOfType(@NotNull PsiElement element, Class<? extends T> @NotNull ... classes) {
    PsiElement run = element;
    while (run != null) {
      for (Class<? extends T> aClass : classes) {
        if (aClass.isInstance(run)) return (T)run;
      }
      if (run instanceof PsiFile || run instanceof PyAstStatementList) break;
      run = run.getParent();
    }

    return null;
  }

  private static boolean inFromImportParentheses(PsiElement statement, int offset) {
    if (!(statement instanceof PyAstFromImportStatement fromImportStatement)) {
      return false;
    }
    PsiElement leftParen = fromImportStatement.getLeftParen();
    if (leftParen != null && offset >= leftParen.getTextRange().getEndOffset()) {
      return true;
    }
    return false;
  }

  private static boolean inWithItemsParentheses(@NotNull PsiElement statement, int offset) {
    if (!(statement instanceof PyAstWithStatement)) {
      return false;
    }

    final PsiElement leftParen = PyPsiUtilsCore.getFirstChildOfType(statement, PyTokenTypes.LPAR);
    return leftParen != null && offset >= leftParen.getTextRange().getEndOffset();
  }

  private static boolean inCaseClauseParentheses(@NotNull PsiElement statement, int offset) {
    if (!(statement instanceof PyAstCaseClause caseClause)) {
      return false;
    }
    final PyAstPattern pattern = caseClause.getPattern();
    if (pattern == null) {
      return false;
    }

    final PsiElement leftParen = PyPsiUtilsCore.getChildByFilter(pattern, PyTokenTypes.OPEN_BRACES, 0);
    return leftParen != null && offset >= leftParen.getTextRange().getEndOffset();
  }
}
