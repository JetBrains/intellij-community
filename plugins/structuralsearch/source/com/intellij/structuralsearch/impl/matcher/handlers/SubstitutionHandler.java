package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.MatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;

import java.util.LinkedList;

/**
 * Matching handler that manages substitutions matching
 */
public class SubstitutionHandler extends Handler {
  private String name;
  private int maxOccurs;
  private int minOccurs;
  private boolean greedy;
  private boolean target;
  private Handler predicate;
  private Handler matchHandler;
  private boolean subtype;
  private boolean strictSubtype;
  // matchedOccurs + 1 = number of item being matched
  private int matchedOccurs;
  private int totalMatchedOccurs = -1;
  private MatchResultImpl myNestedResult;

  public SubstitutionHandler(final String name, final boolean target, int minOccurs,
                             int maxOccurs, boolean greedy) {
    this.name = name;
    this.maxOccurs = maxOccurs;
    this.minOccurs = minOccurs;
    this.target = target;
    this.greedy = greedy;
  }

  public boolean isSubtype() {
    return subtype;
  }

  public boolean isStrictSubtype() {
    return strictSubtype;
  }

  public void setStrictSubtype(boolean strictSubtype) {
    this.strictSubtype = strictSubtype;
  }

  public void setSubtype(boolean subtype) {
    this.subtype = subtype;
  }

  public void setPredicate(Handler handler) {
    predicate = handler;
  }

  // Matcher

  public Handler getPredicate() {
    return predicate;
  }

  private static boolean validateOneMatch(final PsiElement match, int start, int end, final MatchResultImpl result, final MatchContext matchContext) {
    final boolean matchresult;

    if (match!=null) {
      if (start==0 && end==-1 && result.getStart()==0 && result.getEnd()==-1) {
        matchresult = matchContext.getMatcher().match(match,result.getMatchRef().getElement());
      } else {
        matchresult = MatchingVisitor.getText(match,start,end).equals(
          result.getMatchImage()
        );
      }
    } else {
      matchresult = result.isMatchImageNull();
    }

    return matchresult;
  }

  public boolean validate(final PsiElement match, int start, int end, MatchContext context) {
    if (predicate!=null) {
      if(!predicate.match(null,match,start,end,context)) return false;
    }

    if (maxOccurs==0) {
      totalMatchedOccurs++;
      return false;
    }

    MatchResultImpl result = context.getResult().findSon(name);
    
    if (result == null && context.getPreviousResult() != null) {
      result = context.getPreviousResult().findSon(name);
    }

    if (result!=null) {
      if (minOccurs == 1 && maxOccurs == 1) {
        // check if they are the same
        return validateOneMatch(match, start, end, result,context);
      } else if (maxOccurs > 1 && totalMatchedOccurs!=-1) {
        final int size = result.getAllSons().size();
        if (matchedOccurs >= size) {
          return false;
        }
        result = size == 0 ?result:(MatchResultImpl)result.getAllSons().get(matchedOccurs);
        // check if they are the same
        return validateOneMatch(match, start, end, result, context);
      }
    }

    return true;
  }

  public boolean match(final PsiElement node, final PsiElement match, MatchContext context) {
    if (!super.match(node,match,context)) return false;
    //MatchResult saveResult = context.getResult();
    //context.setResult(null);

    boolean result = matchHandler == null ?
      context.getMatcher().match(node,match):
      matchHandler.match(node,match,context);
    //if (context.hasResult() && saveResult!=null) {
    //  saveResult.addSon(context.getResult());
    //}
    //context.setResult(saveResult);

    return result;
  }

  public boolean handle(final PsiElement match, MatchContext context) {
    return handle(match,0,-1,context);
  }

  public void addResult(PsiElement match,int start, int end,MatchContext context) {
    if (totalMatchedOccurs == -1) {
      final MatchResultImpl matchResult = context.getResult();
      final MatchResultImpl substitution = matchResult.findSon(name);

      if (substitution == null) {
        matchResult.addSon( createMatch(match,start,end) );
      } else if (maxOccurs > 1) {
        final MatchResultImpl result = createMatch(match,start,end);
  
        if (!substitution.isMultipleMatch()) {
          // adding intermediate node to contain all multiple matches
          MatchResultImpl sonresult = new MatchResultImpl(
            substitution.getName(),
            substitution.getMatchImage(),
            substitution.getMatchRef(),
            substitution.getStart(),
            substitution.getEnd(),
            target
          );

          sonresult.setParent(substitution);
          substitution.setMatchRef(
            new SmartPsiPointer(match == null ? null : match)
          );

          substitution.setMultipleMatch(true);

          if (substitution.isScopeMatch()) {
            substitution.setScopeMatch(false);
            sonresult.setScopeMatch(true);
            for(MatchResult r:substitution.getAllSons()) sonresult.addSon((MatchResultImpl)r);
            substitution.clearMatches();
          }

          substitution.addSon( sonresult);
        } 
  
        result.setParent(substitution);
        substitution.addSon( result );
      }
    }
  }

  public boolean handle(final PsiElement match, int start, int end, MatchContext context) {
    if (!validate(match,start,end,context)) { 
      myNestedResult = null;
      
      if (maxOccurs==1 && minOccurs==1) {
        if (context.hasResult()) context.getResult().removeSon(name);
      }
      // @todo we may fail fast the match by throwing an exception

      return false;
    }

    addResult(match, start, end, context);

    return true;
  }

  private MatchResultImpl createMatch(final PsiElement match, int start, int end) {
    final String image = match == null ? null : MatchingVisitor.getText(match, start, end);
    final SmartPsiPointer ref = new SmartPsiPointer(match);

    final MatchResultImpl result = myNestedResult == null ? new MatchResultImpl(
      name,
      image,
      ref,
      start,
      end,
      target
    ) : myNestedResult;

    if (myNestedResult != null) {
      myNestedResult.setName( name );
      myNestedResult.setMatchImage( image );
      myNestedResult.setMatchRef( ref );
      myNestedResult.setStart( start );
      myNestedResult.setEnd( end );
      myNestedResult.setTarget( target );
      myNestedResult = null;
    }

    return result;
  }

  public static final String getTypedVarString(final PsiElement element) {
    String text;

    if (element instanceof PsiNamedElement) {
      text = ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PsiAnnotation) {
      PsiJavaCodeReferenceElement referenceElement = ((PsiAnnotation)element).getNameReferenceElement();
      text = referenceElement == null ? null : referenceElement.getQualifiedName();
    }
    else if (element instanceof PsiNameValuePair) {
      text = ((PsiNameValuePair)element).getName();
    }
    else {
      text = element.getText();
      if (StringUtil.startsWithChar(text, '@')) {
        text = text.substring(1);
      }
      if (StringUtil.endsWithChar(text, ';')) text = text.substring(0, text.length() - 1);
      else if (element instanceof PsiExpressionStatement) {
        int i = text.indexOf(';');
        if (i != -1) text = text.substring(0,i);
      }
    }

    if (text==null) text = element.getText();
    
    return text;
  }

  static final Class MEMBER_CONTEXT = PsiMember.class;
  static final Class EXPR_CONTEXT = PsiExpression.class;

  static Class getElementContextByPsi(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      element = element.getParent();
    }

    if (element instanceof PsiMember) {
      return MEMBER_CONTEXT;
    } else {
      return EXPR_CONTEXT;
    }
  }

  boolean validate(MatchContext context, Class elementContext) {
    MatchResult substitution = context.getResult().findSon(name);

    if (minOccurs >= 1 &&
        ( substitution == null ||
          getElementContextByPsi(substitution.getMatchRef().getElement()) != elementContext
        )
       ) {
      return false;
    } else if (maxOccurs <= 1 &&
        substitution!=null && substitution.hasSons()
    ) {
      return false;
    } else if (maxOccurs==0 && totalMatchedOccurs!=-1) {
      return false;
    }
    return true;
  }

  public int getMinOccurs() {
    return minOccurs;
  }

  public int getMaxOccurs() {
    return maxOccurs;
  }

  private final void removeLastResults(int numberOfResults, MatchContext context) {
    if (numberOfResults == 0) return;
    MatchResultImpl substitution = context.getResult().findSon(name);

    if (substitution!=null) {
      if (substitution.hasSons()) {
        LinkedList sons = (LinkedList) substitution.getMatches();

        while(numberOfResults > 0) {
          --numberOfResults;
          sons.removeLast();
        }

        if (sons.size() == 0) {
          context.getResult().removeSon(name);
        }
      } else {
        context.getResult().removeSon(name);
      }
    }
  }

  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    matchedOccurs = 0;

    while(nodes2.hasNext() && matchedOccurs < minOccurs) {
      if (match(nodes.current(),nodes2.current(),context)) {
        ++matchedOccurs;
      } else {
        break;
      }
      nodes2.advance();
    }

    if (matchedOccurs!=minOccurs) {
      // failed even for min occurs
      removeLastResults(matchedOccurs,context);
      nodes2.rewind(matchedOccurs);
      return false;
    }

    if (greedy) {
      // go greedily to maxOccurs

      while(nodes2.hasNext() && matchedOccurs < maxOccurs) {
        if (match(nodes.current(),nodes2.current(),context)) {
          ++matchedOccurs;
        } else {
          // no more matches could take!
          break;
        }
        nodes2.advance();
      }

      nodes.advance();

      if (nodes.hasNext()) {
        final Handler nextHandler = context.getPattern().getHandler(nodes.current());

        while(matchedOccurs >= minOccurs) {
          if (nextHandler.matchSequentially(nodes,nodes2,context)) {
            totalMatchedOccurs = matchedOccurs;
            // match found
            return true;
          }

          if (matchedOccurs > 0) {
            nodes2.rewind();
            removeLastResults(1,context);
          }
          --matchedOccurs;
        }

        if (matchedOccurs > 0) {
          removeLastResults(matchedOccurs,context);
        }
        nodes.rewind();
        return false;
      } else {
        // match found
        if (!nodes2.hasNext()) {
          return checkSameOccurencesConstraint();
        }
        removeLastResults(matchedOccurs,context);
        return false;
      }
    } else {
      nodes.advance();

      if (nodes.hasNext()) {
        final Handler nextHandler = context.getPattern().getHandler(nodes.current());

        while(nodes2.hasNext() && matchedOccurs <= maxOccurs) {
          if (nextHandler.matchSequentially(nodes,nodes2,context)) {
            return checkSameOccurencesConstraint();
          } else if (match(nodes.current(),nodes2.current(),context)) {
            matchedOccurs++;
          } else {
            nodes.rewind();
            removeLastResults(matchedOccurs,context);
            return false;
          }
          nodes2.advance();
        }

        nodes.rewind();
        removeLastResults(matchedOccurs,context);
        return false;
      } else {
        return checkSameOccurencesConstraint();
      }
    }
  }

  private final boolean checkSameOccurencesConstraint() {
    if (totalMatchedOccurs == -1) {
      totalMatchedOccurs = matchedOccurs;
      return true;
    }
    else {
      return totalMatchedOccurs == matchedOccurs;
    }
  }

  public void setTarget(boolean target) {
    this.target = target;
  }

  public Handler getMatchHandler() {
    return matchHandler;
  }

  public void setMatchHandler(Handler matchHandler) {
    this.matchHandler = matchHandler;
  }

  public boolean isTarget() {
    return target;
  }

  public String getName() {
    return name;
  }

  public void reset() {
    totalMatchedOccurs = -1;
  }

  public boolean shouldAdvanceThePatternFor(PsiElement patternElement, PsiElement matchedElement) {
    if(maxOccurs > 1) return false;
    return super.shouldAdvanceThePatternFor(patternElement,matchedElement);
  }

  public void setNestedResult(final MatchResultImpl nestedResult) {
    myNestedResult = nestedResult;
  }

  public MatchResultImpl getNestedResult() {
    return myNestedResult;
  }
}
