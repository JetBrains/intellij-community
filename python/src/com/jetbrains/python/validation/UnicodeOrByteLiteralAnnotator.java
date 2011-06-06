package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marks string literals as byte or Unicode.
 * <br/>
 * User: dcheryasov
 */
public class UnicodeOrByteLiteralAnnotator extends PyAnnotator {

  private LanguageLevel myLanguageLevel = null;
  private Boolean myUnicodeImported = null;

  private static final Pattern N_ESC_PATTERN = Pattern.compile("N\\{([A-Za-z][A-Za-z_ 0-9]*\\}?)"); // N{whatever; $1 ends with '}' in correct case.
  private static final String ALLOWED_ESCAPES = "\nabfnNrtuUvx\\01234567"; // chars allowed after backslash

  private boolean isDefaultUnicode(@NotNull PsiElement node) {
    boolean ret;
    if (myLanguageLevel == null) {
      myLanguageLevel = LanguageLevel.forElement(node);
    }
    ret = myLanguageLevel.isAtLeast(LanguageLevel.PYTHON30);
    if (myUnicodeImported == null) {
      final PsiFile file = node.getContainingFile();
      if (file instanceof PyFile) {
        myUnicodeImported = ((PyFile)file).hasImportFromFuture(FutureFeature.UNICODE_LITERALS);
      }
    }
    if (myUnicodeImported != null) ret |= myUnicodeImported;
    return ret;
  }

  @Override
  public void visitPyFile(PyFile node) {
    myLanguageLevel = null;
    myUnicodeImported = null;
  }

  private static boolean isRaw(CharSequence literal) {
    if (literal.length() > 0) { // no real literal is shorter than 2, but tests provide some degenerate cases
      char first_char = literal.charAt(0);
      if (first_char == 'r' || first_char == 'R') return true;
      if (literal.length() > 1) {
        char second_char = literal.charAt(1);
        if (second_char == 'r' || second_char == 'R') return true;
      }
    }
    return false;
  }
  
  @Override
  public void visitPyStringLiteralExpression(PyStringLiteralExpression expr) {
    List<ASTNode> literal_nodes = expr.getStringNodes();
    for (ASTNode node : literal_nodes) {
      int start = node.getStartOffset();
      CharSequence text = node.getChars();
      int length = text.length();
      if (length > 0) {
        char first_char = Character.toLowerCase(text.charAt(0));
        boolean is_unicode = isDefaultUnicode(expr);
        is_unicode |= (first_char == 'u');
        is_unicode &= (first_char != 'b');
        if (is_unicode) {
          getHolder().createInfoAnnotation(node, null).setTextAttributes(PyHighlighter.PY_UNICODE_STRING);
        }
        final boolean is_raw = isRaw(text);
        // highlight escapes
        Matcher n_matcher = N_ESC_PATTERN.matcher(text);
        int pos = 0;
        while (pos < length) {
          // find a backslash
          while (pos < length && text.charAt(pos) != '\\') pos += 1;
          if (pos < length) {
            if (pos < length-1) {
              // pos is where the backslash is
              char escaped_char = text.charAt(pos + 1);
              if (ALLOWED_ESCAPES.indexOf(escaped_char) >= 0) {
                if (escaped_char == 'x' && ! is_raw) {
                  int span = 4; // 4 = len("\\xNN")
                  checkHexEscape(start, text, length, pos, span);
                }
                else if (is_unicode && escaped_char == 'u') {
                  int span = 6; // 6 = len("\\uNNNN")
                  checkHexEscape(start, text, length, pos, span);
                }
                else if (is_unicode && escaped_char == 'U') {
                  int span = 10; // 10 = len("\\Unnnnnnnnn")
                  checkHexEscape(start, text, length, pos, span);
                }
                else if (is_unicode && escaped_char == 'N' && ! is_raw) {
                  if (n_matcher.find(pos+1)) {
                    if (n_matcher.group(1).endsWith("}")) markAsValidEscape(start + pos, start + n_matcher.end(1));
                    else markAsInvalidEscape(start + pos, start + n_matcher.end(1));
                  }
                  else markAsInvalidEscape(start + pos, start + pos + 2); // 3 is len("\\N")
                }
                else if (escaped_char >= '0' && escaped_char <= '7' && ! is_raw) {
                  int span = 4; // 4 = len("\\ooo")
                  if (pos < length-span) {
                    markAsValidEscape(start + pos, start + firstNonOctalPos(text, pos+1, pos + span));
                  }
                }
                else { // plain 1-char escape, unless it's Unicode-specific in byte-mode
                  if ((is_unicode || "UuN".indexOf(escaped_char) < 0) && ! is_raw)
                  markAsValidEscape(start + pos, start+pos+2);
                }
              } // else: a non-interpreted sequence like \Q: not an error, just don't highlight
            }
            // else: lone backslash at EOL, we ignore it
          }
          pos += 1;
        }

      }
    }
  }

  private void checkHexEscape(int start, CharSequence text, int length, int pos, int span) {
    if (pos < length-span) {
      int end_pos = pos+span;
      if (allHex(text, pos+2, end_pos)) markAsValidEscape(start+pos, start+end_pos);
      else markAsInvalidEscape(start+pos, start+end_pos);
    }
    else markAsInvalidEscape(start+pos, start+length-1);
  }

  // how many octal characters are there in [start, end)
  private static int firstNonOctalPos(CharSequence text, int start, int end) {
    int i;
    for (i=start; i<end; i+=1) {
      char c = text.charAt(i);
      if (c < '0' || c > '7') break;
    }
    return i;
  }

  private static boolean allHex(CharSequence text, int start, int end) {
    for (int i=start; i<end; i+=1) {
      if (! isHexDigit(text.charAt(i))) return false;
    }
    return true;
  }

  private static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private void markAsValidEscape(int start, int end) {
    getHolder().createInfoAnnotation(new TextRange(start, end), null).setTextAttributes(PyHighlighter.PY_VALID_STRING_ESCAPE);
  }

  private void markAsInvalidEscape(int start, int end) {
    getHolder().createErrorAnnotation(new TextRange(start, end), "Invalid escape sequence").setTextAttributes(PyHighlighter.PY_INVALID_STRING_ESCAPE);
  }
}
