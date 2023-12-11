// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Joins lines sanely.
 * <ul>
 * <li>statement lines: add a semicolon;</li>
 * <li>list-like lines: keep one space after comma;</li>
 * <li>lines inside a multiline string: remove excess indentation;</li>
 * <li>multi-constant string like "a" "b": join into one;</li>
 * <li>comment and comment: remove indentation and hash sign;</li>
 * <li>second line is 'class' or 'def': fail.</li>
 * </ul>
 */
public final class PyJoinLinesHandler implements JoinRawLinesHandlerDelegate {
  private final static Joiner[] JOINERS = {
    new OpenBracketJoiner(),
    new CloseBracketJoiner(),
    new StringLiteralJoiner(),
    new StmtJoiner(), // strings before stmts to let doc strings join
    new BinaryExprJoiner(),
    new StripBackslashJoiner()
  };


  @Override
  public int tryJoinLines(@NotNull Document document, @NotNull PsiFile file, int start, int end) {
    return -1; // we go for raw
  }

  @Override
  public int tryJoinRawLines(@NotNull Document document, @NotNull PsiFile file, int start, int end) {
    if (!(file instanceof PyFile)) return CANNOT_JOIN;

    // step back the probable "\" and space before it.
    final CharSequence text = document.getCharsSequence();
    if (start >= 0 && text.charAt(start) == '\n') start -= 1;
    if (start >= 0 && text.charAt(start) == '\\') start -= 1;
    while (start >= 0 && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) {
      start -= 1;
    }
    if (start < 0) {
      return CANNOT_JOIN; // TODO: join with empty BOF, too
    }

    // detect elements around the join
    final PsiElement leftElement = file.findElementAt(start);
    final PsiElement rightElement = file.findElementAt(end);
    if (leftElement != null && rightElement != null) {
      final PyExpression leftExpr = PsiTreeUtil.getParentOfType(leftElement, PyExpression.class);
      final PyExpression rightExpr = PsiTreeUtil.getParentOfType(rightElement, PyExpression.class);

      final Request request = new Request(document, start, end, leftElement, leftExpr, rightElement, rightExpr);

      for (Joiner joiner : JOINERS) {
        final Result res = joiner.join(request);
        if (res != null) {
          final int cutStart = start + 1 - res.cutFromLeft;
          document.replaceString(cutStart, end + res.cutIntoRight, res.replacement);
          return cutStart + res.caretOffset;
        }
      }
    }
    return CANNOT_JOIN;
  }

  // a dumb immutable result holder
  private static class Result {
    final String replacement;
    final int caretOffset;
    final int cutFromLeft;
    final int cutIntoRight;

    /**
     * Result of a join operation.
     *
     * @param replacement:       what string to insert at start position
     * @param cursorOffset: how to move cursor relative to start (0 = stand at start)
     */
    Result(@NotNull String replacement, int cursorOffset) {
      this(replacement, cursorOffset, 0, 0);
    }

    /**
     * Result of a join operation.
     *
     * @param replacement       what to insert into the cut place
     * @param cursorOffset where to put cursor, relative to the start cursorOffset of cutting
     * @param cutFromLeft  how many chars to cut from the end on left string, >0 moves start cursorOffset of cutting to the left.
     * @param cutIntoRight how many chars to cut from the beginning on right string, >0 moves start cursorOffset of cutting to the right.
     */
    Result(@NotNull String replacement, int cursorOffset, int cutFromLeft, int cutIntoRight) {
      this.cutFromLeft = cutFromLeft;
      this.cutIntoRight = cutIntoRight;
      this.replacement = replacement;
      caretOffset = cursorOffset;
    }
  }

  // a dumb immutable request items holder
  private static final class Request {
    final Document document;
    final PsiElement leftElem;
    final PsiElement rightElem;
    final PyExpression leftExpr;
    final PyExpression rightExpr;
    final int secondLineStartOffset;
    final int firstLineEndOffset;

    private Request(@NotNull Document document,
                    int firstLineEndOffset,
                    int secondLineStartOffset,
                    @NotNull PsiElement leftElem,
                    @Nullable PyExpression leftExpr,
                    @NotNull PsiElement rightElem,
                    @Nullable PyExpression rightExpr) {
      this.document = document;
      this.firstLineEndOffset = firstLineEndOffset;
      this.secondLineStartOffset = secondLineStartOffset;
      this.leftElem = leftElem;
      this.rightElem = rightElem;
      this.leftExpr = leftExpr;
      this.rightExpr = rightExpr;
    }
  }

  private interface Joiner {
    /**
     * Try to join lines.
     *
     * @param req@return null if cannot join, or ("what to insert", cursor_offset).
     */
    @Nullable
    Result join(@NotNull Request req);
  }

  private static class OpenBracketJoiner implements Joiner {
    private static final TokenSet OPENS = TokenSet.create(PyTokenTypes.LBRACKET, PyTokenTypes.LBRACE, PyTokenTypes.LPAR);

    @Override
    public Result join(@NotNull Request req) {
      if (OPENS.contains(req.leftElem.getNode().getElementType())) {
        // TODO: look at settings for space after opening paren
        return new Result("", 0);
      }
      return null;
    }
  }

  private static class CloseBracketJoiner implements Joiner {
    private static final TokenSet CLOSES = TokenSet.create(PyTokenTypes.RBRACKET, PyTokenTypes.RBRACE, PyTokenTypes.RPAR);

    @Override
    public Result join(@NotNull Request req) {
      if (CLOSES.contains(req.rightElem.getNode().getElementType())) {
        // TODO: look at settings for space before closing paren
        return new Result("", 0);
      }
      return null;
    }
  }

  private static class BinaryExprJoiner implements Joiner {
    @Override
    public Result join(@NotNull Request req) {
      if (req.leftExpr instanceof PyBinaryExpression || req.rightExpr instanceof PyBinaryExpression) {
        // TODO: look at settings for space around binary exprs
        return new Result(" ", 1);
      }
      return null;
    }
  }

  private static class StmtJoiner implements Joiner {
    @Override
    public Result join(@NotNull Request req) {
      final PyStatement leftStmt = PsiTreeUtil.getParentOfType(req.leftExpr, PyStatement.class);
      if (leftStmt != null) {
        final PyStatement rightStmt = PsiTreeUtil.getParentOfType(req.rightExpr, PyStatement.class);
        if (rightStmt != null && rightStmt != leftStmt) {
          // TODO: look at settings for space after semicolon
          return new Result("; ", 1); // cursor after semicolon
        }
      }
      return null;
    }
  }

  private static class StringLiteralJoiner implements Joiner {
    @Override
    public Result join(@NotNull Request req) {
      final PyStringElement leftStringElem = PsiTreeUtil.getParentOfType(req.leftElem, PyStringElement.class, false);
      final PyStringElement rightStringElem = PsiTreeUtil.getParentOfType(req.rightElem, PyStringElement.class, false);
      if (leftStringElem != null && rightStringElem != null && leftStringElem != rightStringElem) {
        final PsiElement parent = rightStringElem.getParent();
        if ((leftStringElem.getParent() == parent && parent instanceof PyStringLiteralExpression) ||
            (req.leftExpr instanceof PyStringLiteralExpression && req.rightExpr instanceof PyStringLiteralExpression)) {

          if (leftStringElem.isTerminated() && rightStringElem.isTerminated() && haveSamePrefixes(leftStringElem, rightStringElem)) {
            final String leftElemQuotes = leftStringElem.getQuote();
            final String rightElemQuotes = rightStringElem.getQuote();
            int quotesMaxLength = Math.max(leftElemQuotes.length(), rightElemQuotes.length());
            int stringToJoinMaxLength = getStringToJoinMaxLength(req, quotesMaxLength);
            final String replacement = findReplacement(rightStringElem.getContent(), stringToJoinMaxLength);

            if (leftElemQuotes.equals(rightElemQuotes)) {
              return getResultAndSplitStringIfTooLong(req, leftStringElem, rightStringElem, replacement, leftElemQuotes);
            }

            return processStringsWithDifferentQuotes(req, leftStringElem, rightStringElem, replacement);
          }
        }
      }
      return null;
    }


    @Nullable
    private static Result processStringsWithDifferentQuotes(@NotNull final Request req,
                                                            @NotNull final PyStringElement leftElem,
                                                            @NotNull final PyStringElement rightElem,
                                                            @NotNull final String replacement) {
      if (!leftElem.isTripleQuoted() && !rightElem.isTripleQuoted()) {
        if (!rightElem.getContent().contains(leftElem.getQuote())) {
          final int quotePos = rightElem.getTextOffset() + rightElem.getContentRange().getEndOffset();
          final String quote = leftElem.getQuote();
          req.document.replaceString(quotePos, quotePos + 1, quote);
          return getResultAndSplitStringIfTooLong(req, leftElem, rightElem, replacement, quote);
        }
        else if (!leftElem.getContent().contains(rightElem.getQuote())) {
          final int quotePos = leftElem.getTextOffset() + leftElem.getContentRange().getStartOffset() - 1;
          final String quote = rightElem.getQuote();
          req.document.replaceString(quotePos, quotePos + 1, quote);
          return getResultAndSplitStringIfTooLong(req, leftElem, rightElem, replacement, quote);
        }
      }
      return null;
    }

    @NotNull
    private static Result getResultAndSplitStringIfTooLong(@NotNull final Request req,
                                                           @NotNull final PyStringElement leftElem,
                                                           @NotNull final PyStringElement rightElem,
                                                           @NotNull final String replacement,
                                                           @NotNull final String quote) {
      int cutIntoRight = rightElem.getContentRange().getStartOffset();
      String lineEnd = "";
      if (!replacement.isEmpty()) {
        cutIntoRight = replacement.length() + rightElem.getQuote().length();
        int contentWithQuoteStartColumn = leftElem.getTextOffset() + leftElem.getPrefixLength() - getLeftLineStartOffset(req);
        int quotePos = rightElem.getTextOffset() + rightElem.getContentRange().getStartOffset() + replacement.length();
        req.document.insertString(quotePos, rightElem.getQuote());
        req.document.insertString(quotePos, StringUtil.repeat(" ", contentWithQuoteStartColumn));
        lineEnd = quote + "\\\n";
      }
      return new Result(replacement + lineEnd, 0, leftElem.getQuote().length(), cutIntoRight);
    }

    private static boolean haveSamePrefixes(@NotNull PyStringElement leftNodeInfo, @NotNull PyStringElement rightNodeInfo) {
      return leftNodeInfo.isUnicode() == rightNodeInfo.isUnicode() &&
             leftNodeInfo.isRaw() == rightNodeInfo.isRaw() &&
             leftNodeInfo.isBytes() == rightNodeInfo.isBytes() &&
             // TODO Merge formatted and plain strings elements (escape curly braces, etc.)
             leftNodeInfo.isFormatted() == rightNodeInfo.isFormatted();
    }
  }

  private static String findReplacement(String text, int maxLength) {
    if (text.length() < maxLength) return "";
    List<String> words = StringUtil.split(text, " ");
    StringBuilder builder = new StringBuilder();
    final int delimiterLength = 1;
    int wordsLength = 0;
    for (String word: words) {
      wordsLength += word.length() + delimiterLength;
      if (wordsLength >= maxLength) break;
      builder.append(word);
      builder.append(" ");
    }
    return builder.toString();
  }

  private static int getStringToJoinMaxLength(Request request, int symbolsToSkip) {
    int leftLineStartOffset = getLeftLineStartOffset(request);
    final int margin = CodeStyle.getSettings(request.leftElem.getContainingFile()).getRightMargin(PythonLanguage.getInstance());
    int leftLineLength = request.document.getLineEndOffset(request.document.getLineNumber(leftLineStartOffset)) - leftLineStartOffset;
    return margin - leftLineLength - symbolsToSkip;
  }

  private static int getLeftLineStartOffset(@NotNull Request req) {
    int lineNumber = req.document.getLineNumber(req.firstLineEndOffset);
    return req.document.getLineStartOffset(lineNumber);
  }

  private static class StripBackslashJoiner implements Joiner {
    static final TokenSet SINGLE_QUOTED_STRINGS = TokenSet.create(PyTokenTypes.SINGLE_QUOTED_STRING, PyTokenTypes.SINGLE_QUOTED_UNICODE);

    @Nullable
    @Override
    public Result join(@NotNull Request req) {
      final String gap = req.document.getText(new TextRange(req.firstLineEndOffset + 1, req.secondLineStartOffset));
      final int index = gap.indexOf('\\');
      if (index >= 0) {
        if (req.leftElem == req.rightElem && SINGLE_QUOTED_STRINGS.contains(req.leftElem.getNode().getElementType())) {
          return new Result(gap.replaceFirst("\\\\\\n", ""), 0);
        }
        else {
          return new Result(gap.substring(0, index), 0);
        }
      }
      return null;
    }
  }
}
