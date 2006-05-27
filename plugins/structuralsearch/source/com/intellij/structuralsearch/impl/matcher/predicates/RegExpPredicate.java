package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.Handler;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.regex.Matcher;

import org.jetbrains.annotations.NonNls;

/**
 * Root of handlers for pattern node matching. Handles simpliest type of the match.
 */
public final class RegExpPredicate extends Handler {
  private Pattern pattern;
  private String baseHandlerName;
  private boolean simpleString;
  private boolean couldBeOptimized;
  private String regexp;
  private boolean caseSensitive;
  private boolean multiline;
  private boolean wholeWords;
  private boolean target;
  private NodeTextGenerator myNodeTextGenerator;

  public interface NodeTextGenerator {
    String getText(PsiElement element);
  }

  public RegExpPredicate(final String regexp, final boolean caseSensitive, final String _baseHandlerName, boolean _wholeWords, boolean _target) {
    couldBeOptimized = containsRegExp(regexp);
    if (!_wholeWords) {
      simpleString = couldBeOptimized;
    }

    this.regexp = regexp;
    this.caseSensitive = caseSensitive;
    this.wholeWords = _wholeWords;
    baseHandlerName = _baseHandlerName;

    if (!simpleString) {
      compilePattern();
    }
    target = _target;
  }

  private static boolean containsRegExp(final String regexp) {
    for(int i=0;i<regexp.length();++i) {
      if(MatchUtils.SPECIAL_CHARS.indexOf(regexp.charAt(i))!=-1) {
        return false;
      }
    }

    return true;
  }

  private void compilePattern() {
    try {
      @NonNls String realRegexp = regexp;
      if (wholeWords) {
        realRegexp = ".*?\\b(?:" + realRegexp + ")\\b.*?";
      }

      pattern = Pattern.compile(
        realRegexp,
        (caseSensitive ? 0: Pattern.CASE_INSENSITIVE) | (multiline ? Pattern.DOTALL:0)
      );
    } catch(PatternSyntaxException ex) {
      throw new MalformedPatternException(SSRBundle.message("error.incorrect.regexp.constraint", regexp, baseHandlerName));
    }
  }

  public boolean couldBeOptimized() {
    return couldBeOptimized;
  }

  public String getRegExp() {
    return regexp;
  }

  /**
   * Attempts to match given handler node against given node.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successfull and false otherwise
   */
  public boolean match(PsiElement node,PsiElement matchedNode, int start, int end, MatchContext context) {
    if (matchedNode==null) return false;
    String text;

    text = myNodeTextGenerator != null ? myNodeTextGenerator.getText(matchedNode) : getMeaningfulText(matchedNode);

    boolean result = doMatch(text, start, end, context, matchedNode);

    if (!result) {
      // Short class name is matched with fully qualified name
      if(matchedNode instanceof PsiJavaCodeReferenceElement || matchedNode instanceof PsiClass) {
        PsiElement element = (matchedNode instanceof PsiJavaCodeReferenceElement)?
                             ((PsiJavaCodeReferenceElement)matchedNode).resolve():
                             matchedNode;

        if (element instanceof PsiClass) {
          text = ((PsiClass)element).getQualifiedName();

          if (text!=null) {
            result = doMatch(text, start, end, context, matchedNode);
          }
        }
      } else if (matchedNode instanceof PsiLiteralExpression) {
        result = doMatch(matchedNode.getText(), start, end, context, matchedNode);
      }
    }

    return result;
  }

  public static String getMeaningfulText(PsiElement matchedNode) {
    String text;
    if (matchedNode instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)matchedNode).getQualifierExpression()!=null
       ) {
      final PsiElement referencedElement = ((PsiReferenceExpression)matchedNode).getReferenceNameElement();
      text = referencedElement != null ? referencedElement.getText():"";
    } else if (matchedNode instanceof PsiLiteralExpression) {
      text = matchedNode.getText();
      //if (text.length()>2 && text.charAt(0)=='"' && text.charAt(text.length()-1)=='"') {
      //  text = text.substring(1,text.length()-1);
      //}
    } else if (matchedNode instanceof XmlAttributeValue) {
      text = matchedNode.getText();
      //if (text.length()>2 &&
      //    ( (text.charAt(0)=='"' && text.charAt(text.length()-1)=='"') ||
      //      (text.charAt(0)=='\'' && text.charAt(text.length()-1)=='\'')
      //    )) {
      //  text = text.substring(1,text.length()-1);
      //}
    } else {
      text = SubstitutionHandler.getTypedVarString(matchedNode);
    }
    return text;
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return match(patternNode,matchedNode,0,-1,context);
  }

  boolean doMatch(String text, MatchContext context, PsiElement matchedElement) {
    return doMatch(text,0,-1,context, matchedElement);
  }

  boolean doMatch(String text, int from, int end, MatchContext context,PsiElement matchedElement) {
    if (from!=0 || end!=-1) {
      text = text.substring(from,end == -1 ? text.length():end);
    }

    if (simpleString) {
      return (caseSensitive)?text.equals(regexp):text.equalsIgnoreCase(regexp);
    }

    final Matcher matcher = pattern.matcher(text);

    if (matcher.matches()) {
      for (int i=1;i<=matcher.groupCount();++i) {
        context.getResult().addSon(
          new MatchResultImpl(
            baseHandlerName + "_" + i,
            matcher.group(i),
            new SmartPsiPointer(matchedElement),
            matcher.start(i),
            matcher.end(i),
            target
          )
        );
      }
      return true;
    } else {
      return false;
    }
  }


  public void setNodeTextGenerator(final NodeTextGenerator nodeTextGenerator) {
    myNodeTextGenerator = nodeTextGenerator;
  }

  public void setMultiline(boolean b) {
    multiline = b;
    compilePattern();
  }

  public boolean isWholeWords() {
    return wholeWords;
  }
}
