package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;

import java.util.List;

/**
 * Looks for well-formedness of string constants.
 *
 * @author dcheryasov
 */
public class StringConstantAnnotator extends PyAnnotator {
  public static final String MISSING_Q = "Missing closing quote";
  private static final String TRIPLE_QUOTES = "\"\"\"";
  private static final String TRIPLE_APOS = "'''";

  //public static final String PREMATURE_Q = "Premature closing quote";
  public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    String s = node.getText();
    String msg = "";
    boolean ok = true;
    boolean esc = false;
    int index = 0;
    // skip 'unicode' and 'raw' modifiers
    char first_quote = s.charAt(index);
    if (Character.toLowerCase(first_quote) == 'u' || Character.toLowerCase(first_quote) == 'b') index += 1;
    first_quote = s.charAt(index);
    if ((first_quote == 'r') || (first_quote == 'R')) index += 1;

    List<ASTNode> stringNodes = node.getStringNodes();
    int thisNodeIndex = index;
    for (ASTNode stringNode : stringNodes) {
      if (checkTripleQuotedString(stringNode.getPsi(), stringNode.getText(), thisNodeIndex, TRIPLE_QUOTES)) return;
      if (checkTripleQuotedString(stringNode.getPsi(), stringNode.getText(), thisNodeIndex, TRIPLE_APOS)) return;
      thisNodeIndex = 0;
    }

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
          if (c == '\\') esc = true;
          // a line may consist of multiple fragments with different quote chars (PY-299)
          else if (c == '\'' || c == '\"') {
            if (first_quote == '\0')
              first_quote = c;
            else if (c == first_quote)
              first_quote = '\0';
          }
          
          /*
          else { // impossible with current lexer, but who knows :)
            msg = PREMATURE_Q + " [" + first_quote  + "]";
            ok = false;
          }
          */
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

  private boolean checkTripleQuotedString(PsiElement node, String s, int index, final String quotes) {
    if (StringUtil.startsWith(s.substring(index, s.length()), quotes)) {
      if (s.length() < 6 + index || !s.endsWith(quotes)) {
        getHolder().createErrorAnnotation(node, "Missing closing triple quotes");
      }
      return true;
    }
    return false;
  }
}
