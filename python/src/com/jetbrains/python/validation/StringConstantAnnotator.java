package com.jetbrains.python.validation;

import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * Looks for well-formedness of string constants.
 * User: dcheryasov
 * Date: Jul 5, 2008
 * Time: 11:58:57 PM
 */
public class StringConstantAnnotator extends PyAnnotator {
  public static final String MISSING_Q = "Missing closing quote";
  public static final String PREMATURE_Q = "Premature closing quote";
  public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    String s = node.getText();
    String msg = "";
    boolean ok = true;
    boolean esc = false;
    int index = 0;
    // skip 'unicode' and 'raw' modifiers
    char first_quote = s.charAt(index);
    if ((first_quote == 'u') || (first_quote == 'U')) index += 1;
    first_quote = s.charAt(index);
    if ((first_quote == 'r') || (first_quote == 'R')) index += 1;
    first_quote = s.charAt(index);
    // s can't begin with a non-quote, else parser would not say it's a string
    index += 1;
    if (index >= s.length()) { // sole opening quote
      msg = MISSING_Q + " [" + first_quote  + "]";
      ok = false;
    }
    else {
      while (ok && (index < s.length()-1)) {
        char c = s.charAt(index);
        if (esc) esc = false;
        else {
          if (c  == first_quote) { // impossible with current lexer, but who knows :)
            msg = PREMATURE_Q + " [" + first_quote  + "]";
            ok = false;
          }
          else if (c  == '\\') esc = true;
        }
        index += 1;
      }
      if (ok && (esc || (s.charAt(index) != first_quote))) {
        msg = MISSING_Q + " [" + first_quote  + "]";
        ok = false;
      }
    }
    //
    if (! ok) {
        getHolder().createErrorAnnotation(node, msg);
    }
  }
}
