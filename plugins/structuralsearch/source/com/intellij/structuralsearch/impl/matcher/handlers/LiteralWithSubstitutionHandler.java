package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 30, 2004
 * Time: 5:07:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class LiteralWithSubstitutionHandler extends Handler {
  private String matchExpression;
  private Matcher matcher;
  private List<SubstitutionHandler> handlers;

  public LiteralWithSubstitutionHandler(String _matchedExpression,List<SubstitutionHandler> _handlers) {
    matchExpression = _matchedExpression;
    handlers = _handlers;
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    final String text = RegExpPredicate.getMeaningfulText(matchedNode);
    int offset = matchedNode.getText().indexOf(text);
    if (matcher==null) {
      matcher = Pattern.compile(matchExpression).matcher(text);
    } else {
      matcher.reset(text);
    }

    while (matcher.find()) {
      for (int i = 0; i < handlers.size(); ++i) {
        SubstitutionHandler handler = handlers.get(i);

        if (!handler.handle(matchedNode,offset + matcher.start(i+1), offset + matcher.end(i+1),context)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
