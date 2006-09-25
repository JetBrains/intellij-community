package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.jetbrains.annotations.NonNls;

/**
 * Handler for doc nodes
 */
public class DocDataHandler extends Handler {
  @NonNls private static final String P_STR = "^\\s*((?:\\w|_|\\-|\\$)+)\\s*(?:=\\s*\"(.*)\"\\s*)?$";
  private static Pattern p = Pattern.compile(
    P_STR,
    Pattern.CASE_INSENSITIVE
  );

  public boolean match(PsiElement node, PsiElement match, MatchContext context) {
    String text1 = node.getText();

    text1 = getTextFromNode(node, text1);

    Matcher m1 = p.matcher(text1);

    String text2 = match.getText();
    text2 = getTextFromNode(match, text2);

    Matcher m2 = p.matcher(text2);

    if (m1.matches() && m2.matches()) {
      String name = m1.group(1);
      String name2 = m2.group(1);
      boolean isTypedName = context.getPattern().isTypedVar(name);

      if (name.equals(name2) || isTypedName) {
        String value = m1.group(2);
        String value2 = m2.group(2);

        if (value!=null) {
          if (value2 == null || !value2.matches(value)) return false;
        }
        if (isTypedName) {
          SubstitutionHandler handler = (SubstitutionHandler) context.getPattern().getHandler(name);
          return handler.handle(match,context);
        }
        return true;
      }
    }
    return text1.equals(text2);
  }

  // since doctag value may be inside doc comment we specially build text including skipped nodes
  private static String getTextFromNode(final PsiElement node, String text1) {
    PsiElement nextSibling = node.getNextSibling();
    if (nextSibling instanceof PsiDocTagValue) {
      text1 += nextSibling.getText();

      nextSibling = nextSibling.getNextSibling();
      if (nextSibling instanceof PsiDocToken && ((PsiDocToken)nextSibling).getTokenType() == PsiDocToken.DOC_COMMENT_DATA) {
        text1 += nextSibling.getText();
      }
    }
    return text1;
  }
}
