package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    int i = start -1;
    CharSequence text = document.getCharsSequence();
    if (i>= 0 && text.charAt(i) == '\\') i -=1;
    while (i>=0 && text.charAt(i) == ' ' || text.charAt(i) == '\t') i -=1;
    if (i < 0) return CANNOT_JOIN; // TODO: join with empty BOF, too

    // detect elements around the join
    PsiElement left_element = file.findElementAt(i);
    PsiElement right_element = file.findElementAt(end);
    if (left_element != null && right_element != null) {
      PyExpression left_expr = PsiTreeUtil.getParentOfType(left_element, PyExpression.class);
      if (left_expr instanceof PsiFile) return CANNOT_JOIN;
      PyExpression right_expr = PsiTreeUtil.getParentOfType(right_element, PyExpression.class);
      if (right_expr instanceof PsiFile) return CANNOT_JOIN;

      Joiner[] joiners = { // these are featherweight, will create and gc instantly
        new OpenBracketJoiner(), new CloseBracketJoiner(),
        new StmtJoiner(), new StringLiteralJoiner(),
        new BinaryExprJoiner(), new ListLikeExprJoiner()
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
    }
    return CANNOT_JOIN;
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
        if (req.leftElem().getParent() == parent && parent instanceof PyStringLiteralExpression) {
          // two quoted strings of same literal
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
              if (! containsChar(text, left_mod.getInnerRange(), right_mod.quote().charAt(0))) {
                int quote_pos = left_mod.getInnerRange().getStartOffset()-1;
                req.document().replaceString(quote_pos, quote_pos+1, right_mod.quote());
                return new Result("", 0, left_mod.quote().length(), right_mod.getStartPadding());
              }
              else {
                if (! containsChar(text, right_mod.getInnerRange(), left_mod.quote().charAt(0))) {
                  int quote_pos = right_mod.getInnerRange().getStartOffset()-1;
                  req.document().replaceString(quote_pos, quote_pos+1, left_mod.quote());
                  return new Result("", 0, left_mod.quote().length(), right_mod.getStartPadding());
                }
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



  @Nullable
  private static Result joinBinaryExpressions(PyExpression leftExpr, PyExpression rightExpr, int start, int end) {
    return null; // XXX remove
  }

  @Nullable
  private static Result joinListLikeExpressions(PyExpression leftExpr, PyExpression rightExpr, int start, int end) {
    return null; // XXX remove
  }

  /**
   Joins a pair of lines,
   */
  private static int[] joinPair(Document document, PsiFile file, int start) {
    final int[] NONE = new int[]{0, 0};
    int[] ret = new int[2];
    PsiElement elt = file.findElementAt(start);
    final PyStringLiteralExpression string_parent = PsiTreeUtil.getParentOfType(elt, PyStringLiteralExpression.class);
    if (string_parent != null) {
      final List<ASTNode> string_nodes = string_parent.getStringNodes();
      // we're inside a string which may consist of a number of string constants
      for (ASTNode node : string_nodes) {
        final TextRange range = node.getTextRange();
        if (range.contains(start) && range.getLength() > 2) {
          final CharSequence text = document.getCharsSequence();
          int first = range.getStartOffset();
          int last = range.getEndOffset();
          boolean is_raw = false;
          if ("Uu".indexOf(text.charAt(first)) > -1 || "Bb".indexOf(text.charAt(first)) > -1) first +=1;
          if ("Rr".indexOf(text.charAt(first)) > -1) {
            is_raw = true;
            first +=1;
          }
          if (last - first >= 6) {
            final char first_char = text.charAt(first); // first_char is a quote, else we'd not have parsed as a string
            if (first_char == text.charAt(first+1)) {
              // we're inside a triple-quoted constant, its min length is 6 and only it can have two quotes at start
              final int our_line_number = document.getLineNumber(first);
              int eol = document.getLineEndOffset(our_line_number);
              if (document.getLineCount() == our_line_number+1) return NONE; // can't join, but it's OK
              if (text.charAt(eol-1) == '\\' &&  is_raw) { // TODO: check true escape
                // convert quoted EOL marker to \n

              }
              else {
                // compress spaces to one
                // TODO
              }
            }
          }
        }
      }
      // not inside any constant but within string expr -> between constants
    }
    return ret;
  }
}
