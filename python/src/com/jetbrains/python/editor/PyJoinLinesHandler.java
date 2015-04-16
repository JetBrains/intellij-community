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
package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

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
 *
 * @author dcheryasov
 */
public class PyJoinLinesHandler implements JoinRawLinesHandlerDelegate {
  private final static Joiner[] JOINERS = {
    new OpenBracketJoiner(),
    new CloseBracketJoiner(),
    new StringLiteralJoiner(),
    new StmtJoiner(), // strings before stmts to let doc strings join
    new BinaryExprJoiner(),
    new CommentJoiner(),
    new StripBackslashJoiner()
  };


  @Override
  public int tryJoinLines(Document document, PsiFile file, int start, int end) {
    return -1; // we go for raw
  }

  @Override
  public int tryJoinRawLines(@NotNull Document document, PsiFile file, int start, int end) {
    if (!(file instanceof PyFile)) return CANNOT_JOIN;

    // step back the probable "\" and space before it.
    final CharSequence text = document.getCharsSequence();
    if (start >= 0 && text.charAt(start) == '\n') start -= 1;
    if (start >= 0 && text.charAt(start) == '\\') start -= 1;
    while (start >= 0 && text.charAt(start) == ' ' || text.charAt(start) == '\t') {
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
  private static class Request {
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
      if (req.leftElem != req.rightElem) {
        final PsiElement parent = req.rightElem.getParent();
        if ((req.leftElem.getParent() == parent && parent instanceof PyStringLiteralExpression) ||
            (req.leftExpr instanceof PyStringLiteralExpression && req.rightExpr instanceof PyStringLiteralExpression)) {
          // two quoted strings close by
          final CharSequence text = req.document.getCharsSequence();
          final StrMod leftMod = new StrMod(text, req.leftElem.getTextRange());
          final StrMod rightMod = new StrMod(text, req.rightElem.getTextRange());
          if (leftMod.isOk() && rightMod.isOk()) {
            final String lquo = leftMod.quote();
            if (leftMod.equals(rightMod)) {
              return new Result("", 0, lquo.length(), rightMod.getStartPadding());
            }
            else if (leftMod.compatibleTo(rightMod) && lquo.length() == 1 && rightMod.quote().length() == 1) {
              // maybe fit one literal's quotes to match other's
              if (!containsChar(text, rightMod.getInnerRange(), leftMod.quote().charAt(0))) {
                final int quotePos = rightMod.getInnerRange().getEndOffset();
                req.document.replaceString(quotePos, quotePos + 1, leftMod.quote());
                return new Result("", 0, leftMod.quote().length(), rightMod.getStartPadding());
              }
              else if (!containsChar(text, leftMod.getInnerRange(), rightMod.quote().charAt(0))) {
                final int quotePos = leftMod.getInnerRange().getStartOffset() - 1;
                req.document.replaceString(quotePos, quotePos + 1, rightMod.quote());
                return new Result("", 0, leftMod.quote().length(), rightMod.getStartPadding());
              }
            }
          }
        }
      }
      return null;
    }

    protected static boolean containsChar(@NotNull CharSequence text, @NotNull TextRange range, char c) {
      return StringUtil.contains(text, range.getStartOffset(), range.getEndOffset(), c);
    }


    private static class StrMod {
      @NotNull private final String myPrefix; // "u", "b", or ""
      private final boolean myRaw; // is raw or not
      @NotNull private final String myQuote; // single or double, one or triple.

      @Nullable private final TextRange myInnerRange;

      public StrMod(@NotNull CharSequence text, @NotNull TextRange range) {
        int pos = range.getStartOffset();
        char c = text.charAt(pos);
        if ("Uu".indexOf(c) > -1 || "Bb".indexOf(c) > -1) {
          myPrefix = String.valueOf(c).toLowerCase(Locale.US);
          pos += 1;
          c = text.charAt(pos);
        }
        else {
          myPrefix = "";
        }
        if ("Rr".indexOf(c) > -1) {
          myRaw = true;
          pos += 1;
          c = text.charAt(pos);
        }
        else {
          myRaw = false;
        }
        final char quote = c;
        if ("'\"".indexOf(quote) < 0) {
          myInnerRange = null;
          myQuote = "";
          return; // failed to find a quote
        }
        // TODO: we could run a simple but complete parser here, only checking escapes
        if (range.getLength() >= 6 && text.charAt(pos + 1) == quote && text.charAt(pos + 2) == quote) {
          myQuote = text.subSequence(pos, pos + 3).toString();
          if (!myQuote.equals(text.subSequence(range.getEndOffset() - 3, range.getEndOffset()).toString())) {
            myInnerRange = null;
            return;
          }
        }
        else {
          myQuote = text.subSequence(pos, pos + 1).toString();
          if (!myQuote.equals(text.subSequence(range.getEndOffset() - 1, range.getEndOffset()).toString())) {
            myInnerRange = null;
            return;
          }
        }
        myInnerRange = TextRange.from(range.getStartOffset() + getStartPadding(), range.getLength() - getStartPadding() - quote().length());
      }

      public boolean isOk() {
        return myInnerRange != null;
      }

      @NotNull
      public String prefix() {
        return myPrefix;
      }

      @NotNull
      public String quote() {
        return myQuote;
      }

      public boolean isRaw() {
        return myRaw;
      }

      @Override
      public boolean equals(Object o) {
        if (o instanceof StrMod) {
          final StrMod other = (StrMod)o;
          return compatibleTo(other) && myQuote.equals(other.quote());
        }
        return false;
      }

      /**
       * @return combined length of initial modifier letters and opening quotes
       */
      public int getStartPadding() {
        return myQuote.length() + myPrefix.length() + (myRaw ? 1 : 0);
      }

      /**
       * @param other
       * @return true iff this and other have the same byte/unicode and raw prefixes.
       */
      public boolean compatibleTo(@NotNull StrMod other) {
        return isOk() && other.isOk() && myRaw == other.isRaw() && myPrefix.equals(other.prefix());
      }
      /**
       * @return range of text part inside quotes
       */
      @Nullable
      public TextRange getInnerRange() {
        return myInnerRange;
      }
    }
  }

  private static class CommentJoiner implements Joiner {
    @Override
    public Result join(@NotNull Request req) {
      if (req.leftElem instanceof PsiComment && req.rightElem instanceof PsiComment) {
        final CharSequence text = req.document.getCharsSequence();
        final TextRange rightRange = req.rightElem.getTextRange();
        final int initialPos = rightRange.getStartOffset() + 1;
        int pos = initialPos; // cut '#'
        final int last = rightRange.getEndOffset();
        while (pos < last && " \t".indexOf(text.charAt(pos)) >= 0) pos += 1;
        final int right = pos - initialPos + 1; // account for the '#'
        return new Result(" ", 0, 0, right);
      }
      return null;
    }
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
