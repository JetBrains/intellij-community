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
package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * Joins lines sanely.
 * - statement lines: add a semicolon;
 * - list-like lines: keep one space after comma;
 * - lines inside a multiline string: remove excess indentation;
 * - multi-constant string like "a" "b": join into one;
 * - comment and comment: remove indentation and hash sign;
 * - second line is 'class' or 'def': fail.
 * <br/>
 * User: dcheryasov
 * Date: Sep 6, 2010 2:25:48 AM
 */
public class PyJoinLinesHandler implements JoinRawLinesHandlerDelegate {

  @Override
  public int tryJoinLines(Document document, PsiFile file, int start, int end) {
    return -1; // we go for raw
  }

  @Override
  public int tryJoinRawLines(Document document, PsiFile file, int start, int end) {
    if (!(file instanceof PyFile)) return CANNOT_JOIN;

    // step back the probable "\" and space before it.
    int i = start;
    CharSequence text = document.getCharsSequence();
    if (i>= 0 && text.charAt(i) == '\n') i -=1;
    if (i>= 0 && text.charAt(i) == '\\') i -=1;
    while (i>=0 && text.charAt(i) == ' ' || text.charAt(i) == '\t') i -=1;
    if (i < 0) return CANNOT_JOIN; // TODO: join with empty BOF, too

    // detect elements around the join
    PsiElement left_element = file.findElementAt(i);
    PsiElement right_element = file.findElementAt(end);
    if (left_element != null && right_element != null) {
      PyExpression left_expr = PsiTreeUtil.getParentOfType(left_element, PyExpression.class);
      if (left_expr instanceof PsiFile) left_expr = null;
      PyExpression right_expr = PsiTreeUtil.getParentOfType(right_element, PyExpression.class);
      if (right_expr instanceof PsiFile) right_expr = null;

      Joiner[] joiners = { // these are featherweight, will create and gc instantly
        new OpenBracketJoiner(), new CloseBracketJoiner(),
        new StringLiteralJoiner(), new StmtJoiner(), // strings before stmts to let doc strings join
        new BinaryExprJoiner(), new ListLikeExprJoiner(),
        new CommentJoiner(),
      };

      Request request = new Request(document, left_element, left_expr, right_element, right_expr);

      for (Joiner joiner : joiners) {
        Result res = joiner.join(request);
        if (res != null) {
          final int cut_start = i + 1 - res.getCutFromLeft();
          document.deleteString(cut_start, end + res.getCutIntoRight());
          document.insertString(cut_start, res.getInsert());
          return cut_start + res.getCursorOffset();
        }
      }

      // single string case PY-4375
      final PyExpression leftExpression = request.leftExpr();
      final PyExpression rightExpression = request.rightExpr();
      if (request.leftElem() == request.rightElem()) {
        IElementType type = request.leftElem().getNode().getElementType();
        if (PyTokenTypes.SINGLE_QUOTED_STRING == type || PyTokenTypes.SINGLE_QUOTED_UNICODE == type) {
          if (leftExpression == null) return CANNOT_JOIN;
          if (removeBackSlash(document, leftExpression, false)) {
            return leftExpression.getTextOffset();
          }
        }
      }
      PsiElement expression = null;
      if (leftExpression != null && rightExpression != null) {
        if (PsiTreeUtil.isAncestor(leftExpression, rightExpression, false))
          expression = leftExpression;
        else if (PsiTreeUtil.isAncestor(rightExpression, leftExpression, false))
          expression = rightExpression;
        if (expression != null && !(expression instanceof PyStringLiteralExpression)) {
          if (removeBackSlash(document, expression, true)) {
            return expression.getTextOffset();
          }
        }
      }
    }
    return CANNOT_JOIN;
  }

  private static boolean removeBackSlash(Document document, PsiElement element, boolean trim) {
    String[] substrings = element.getText().split("\n");
    if (substrings.length != 1) {
      StringBuilder replacement = new StringBuilder();
      for (int i = 0; i < substrings.length; i++) {
        String string = substrings[i];
        if (trim) {
          string = StringUtil.trimLeading(string);
        }
        if (string.trim().endsWith("\\")) {
          replacement.append(string.substring(0, string.length() - 1));
        }
        else {
          replacement.append(string);
        }

        if (i != substrings.length - 1 && !(element instanceof PyReferenceExpression) &&
            !(element instanceof PyStringLiteralExpression)) {
          replacement.append(" ");
        }
      }
      document.replaceString(element.getTextOffset(), element.getTextOffset()+element.getTextLength(), replacement);
      return true;
    }
    return false;
  }

  // a dumb immutable result holder
  private static class Result {
    final String myInsert;
    final int myOffset;
    final int myCutFromLeft;
    final int myCutIntoRight;

    /**
     * Result of a join operation.
     * @param insert: what string to insert at start position
     * @param cursorOffset: how to move cursor relative to start (0 = stand at start)
     */
    Result(String insert, int cursorOffset) {
      myInsert = insert;
      myOffset = cursorOffset;
      myCutFromLeft = 0;
      myCutIntoRight = 0;
    }

    /**
     * Result of a join operation.
     * @param insert what to insert into the cut place
     * @param cursorOffset where to put cursor, relative to the start cursorOffset of cutting
     * @param cutFromLeft how many chars to cut from the end on left string, >0 moves start cursorOffset of cutting to the left.
     * @param cutIntoRight how many chars to cut from the beginning on right string, >0 moves start cursorOffset of cutting to the right.
     */
    private Result(String insert, int cursorOffset, int cutFromLeft, int cutIntoRight) {
      myCutFromLeft = cutFromLeft;
      myCutIntoRight = cutIntoRight;
      myInsert = insert;
      myOffset = cursorOffset;
    }

    public String getInsert() {
      return myInsert;
    }

    public int getCursorOffset() {
      return myOffset;
    }

    public int getCutFromLeft() {
      return myCutFromLeft;
    }

    public int getCutIntoRight() {
      return myCutIntoRight;
    }
  }

  // a dumb immutable request items holder
  private static class Request {
    final Document myDocument;
    final PsiElement myLeftElem;
    final PsiElement myRightElem;
    final PyExpression myLeftExpr;
    final PyExpression myRightExpr;

    private Request(Document document, PsiElement leftElem, PyExpression leftExpr, PsiElement rightElem, PyExpression rightExpr) {
      myDocument = document;
      myLeftElem = leftElem;
      myLeftExpr = leftExpr;
      myRightElem = rightElem;
      myRightExpr = rightExpr;
    }

    public Document document() {
      return myDocument;
    }

    public PsiElement leftElem() {
      return myLeftElem;
    }

    public PyExpression leftExpr() {
      return myLeftExpr;
    }

    public PsiElement rightElem() {
      return myRightElem;
    }

    public PyExpression rightExpr() {
      return myRightExpr;
    }

  }

  private static abstract class Joiner {
    /**
     * Try to join lines.
     *
     * @param req@return null if cannot join, or ("what to insert", cursor_offset).
     */
    @Nullable
    abstract public Result join(Request req);
  }

  private static class OpenBracketJoiner extends Joiner {
    private static TokenSet OPENS = TokenSet.create(PyTokenTypes.LBRACKET, PyTokenTypes.LBRACE, PyTokenTypes.LPAR);
    @Override
    public Result join(Request req) {
      if (OPENS.contains(req.leftElem().getNode().getElementType())) {
        // TODO: look at settings for space after opening paren
        return new Result("", 0);
      }
      return null;
    }
  }

  private static class CloseBracketJoiner extends Joiner {
    private static TokenSet CLOSES = TokenSet.create(PyTokenTypes.RBRACKET, PyTokenTypes.RBRACE, PyTokenTypes.RPAR);
    @Override
    public Result join(Request req) {
      if (CLOSES.contains(req.rightElem().getNode().getElementType())) {
        // TODO: look at settings for space before closing paren
        return new Result("", 0);
      }
      return null;
    }
  }

  private static class BinaryExprJoiner extends Joiner {
    @Override
    public Result join(Request req) {
      if (req.leftExpr() instanceof PyBinaryExpression || req.rightExpr() instanceof PyBinaryExpression) {
        // TODO: look at settings for space around binary exprs
        return new Result(" ", 1);
      }
      return null;
    }
  }

  private static class ListLikeExprJoiner extends Joiner {
    @Override
    public Result join(Request req) {
      final boolean left_is_list_like = PyUtil.instanceOf(req.leftExpr(), PyListLiteralExpression.class, PyTupleExpression.class);
      if (left_is_list_like || PyUtil.instanceOf(req.rightExpr(), PyListLiteralExpression.class, PyTupleExpression.class)
      ) {
        String insert = "";
        if (left_is_list_like) { // we join "a, \n b", not "a \n ,b"
          insert = " "; // TODO: look at settings for space after commas in lists
        }
        return new Result(insert, insert.length());
      }
      return null;
    }
  }

  private static class StmtJoiner extends Joiner {
    @Override
    public Result join(Request req) {
      PyStatement left_stmt = PsiTreeUtil.getParentOfType(req.leftExpr(), PyStatement.class);
      if (left_stmt != null) {
        PyStatement right_stmt = PsiTreeUtil.getParentOfType(req.rightExpr(), PyStatement.class);
        if (right_stmt != null && right_stmt != left_stmt) {
          // TODO: look at settings for space after semicolon
          return new Result("; ", 1); // cursor after semicolon
        }
      }
      return null;
    }
  }

  private static class StringLiteralJoiner extends Joiner {
    @Override
    public Result join(Request req) {
      if (req.leftElem() != req.rightElem()) {
        final PsiElement parent = req.rightElem().getParent();
        if ((req.leftElem().getParent() == parent && parent instanceof PyStringLiteralExpression) ||
            (req.leftExpr() instanceof PyStringLiteralExpression && req.rightExpr() instanceof PyStringLiteralExpression)
        ) {
          // two quoted strings close by
          CharSequence text = req.document().getCharsSequence();
          StrMod left_mod = new StrMod(text, req.leftElem().getTextRange());
          StrMod right_mod = new StrMod(text, req.rightElem().getTextRange());
          if (left_mod.isOk() && right_mod.isOk()) {
            final String lquo = left_mod.quote();
            if (left_mod.equals(right_mod)) {
              return new Result("", 0, lquo.length(), right_mod.getStartPadding());
            }
            else if (left_mod.compatibleTo(right_mod) && lquo.length() == 1 && right_mod.quote().length() == 1) {
              // maybe fit one literal's quotes to match other's
              if (! containsChar(text, right_mod.getInnerRange(), left_mod.quote().charAt(0))) {
                int quote_pos = right_mod.getInnerRange().getEndOffset();
                req.document().replaceString(quote_pos, quote_pos+1, left_mod.quote());
                return new Result("", 0, left_mod.quote().length(), right_mod.getStartPadding());
              }
              else if (! containsChar(text, left_mod.getInnerRange(), right_mod.quote().charAt(0))) {
                int quote_pos = left_mod.getInnerRange().getStartOffset()-1;
                req.document().replaceString(quote_pos, quote_pos+1, right_mod.quote());
                return new Result("", 0, left_mod.quote().length(), right_mod.getStartPadding());
              }
            }
          }
        }
      }
      return null;
    }

    protected static boolean containsChar(CharSequence text, TextRange range, char c) {
      for (int i=range.getStartOffset(); i <= range.getEndOffset(); i+=1) {
        if (text.charAt(i) == c) return true;
      }
      return false;
    }

    private static class StrMod {
      private final String myPrefix; // "u", "b", or ""
      private final boolean myRaw; // is raw or not
      private final String myQuote; // single or double, one or triple.
      private final boolean myOk; // true if parsing went ok
      private final TextRange myInnerRange;

      public StrMod(CharSequence text, TextRange range) {
        int pos = range.getStartOffset();
        char c = text.charAt(pos);
        if ("Uu".indexOf(c) > -1 || "Bb".indexOf(c) > -1) {
          myPrefix = String.valueOf(c).toLowerCase();
          pos +=1;
          c = text.charAt(pos);
        }
        else myPrefix = "";
        if ("Rr".indexOf(c) > -1) {
          myRaw = true;
          pos +=1;
          c = text.charAt(pos);
        }
        else myRaw = false;
        char quote = c;
        if ("'\"".indexOf(quote) < 0) {
          myInnerRange = null;
          myQuote = "";
          myOk = false;
          return; // failed to find a quote
        }
        // TODO: we could run a simple but complete parser here, only checking escapes
        if (range.getLength() >= 6 && text.charAt(pos+1) == quote && text.charAt(pos+2) == quote) {
          myQuote = text.subSequence(pos, pos+3).toString();
          if (!myQuote.equals(text.subSequence(range.getEndOffset()-3, range.getEndOffset()).toString())) {
            myInnerRange = null;
            myOk = false;
            return;
          }
        }
        else {
          myQuote = text.subSequence(pos, pos+1).toString();
          if (!myQuote.equals(text.subSequence(range.getEndOffset()-1, range.getEndOffset()).toString())) {
            myInnerRange = null;
            myOk = false;
            return;
          }
        }
        myInnerRange = TextRange.from(range.getStartOffset()+getStartPadding(), range.getLength()-getStartPadding()-quote().length());
        myOk = true;
      }

      public boolean isOk() {
        return myOk;
      }

      public String prefix() {
        return myPrefix;
      }

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
          return (
            myOk && other.isOk() &&
            myRaw == other.isRaw() &&
            myPrefix.equals(other.prefix()) &&
            myQuote.equals(other.quote())
          );
        }
        return false;
      }

      /**
       * @return combined length of initial modifier letters and opening quotes
       */
      public int getStartPadding() {
        return myQuote.length() + myPrefix.length() + (myRaw? 1 : 0);
      }

      /**
       * @param other
       * @return true iff this and other have the same byte/unicode and raw prefixes.
       */
      public boolean compatibleTo(StrMod other) {
        return myOk && other.isOk() && myRaw == other.isRaw() && myPrefix.equals(other.prefix());
      }

      /**
       * @return range of text part inside quotes
       */
      public TextRange getInnerRange() {
        return myInnerRange;
      }
    }

  }


  private static class CommentJoiner extends Joiner {
    @Override
    public Result join(Request req) {
      if (req.leftElem() instanceof PsiComment && req.rightElem() instanceof PsiComment) {
        CharSequence text = req.document().getCharsSequence();
        final TextRange right_range = req.rightElem().getTextRange();
        int initial_pos = right_range.getStartOffset() + 1;
        int pos = initial_pos; // cut '#'
        int last = right_range.getEndOffset();
        while (pos < last && " \t".indexOf(text.charAt(pos)) >= 0) pos += 1;
        int right = pos - initial_pos + 1; // account for the '#'
        return new Result(" ", 0, 0, right);
      }
      return null;
    }
  }


}
