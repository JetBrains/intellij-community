package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 31.12.2004
 * Time: 12:01:29
 * To change this template use File | Settings | File Templates.
 */
public class DeclarationStatementHandler extends MatchingHandler {
  private MatchingHandler myCommentHandler;

  public boolean match(PsiElement patternNode,PsiElement matchedNode, MatchContext context) {
    if (patternNode instanceof PsiComment) {
      //if (matchedNode instanceof  PsiComment || matchedNode instanceof PsiClass || matchedNode instanceof PsiField)
        return myCommentHandler.match(patternNode, matchedNode, context);
      //return false;
    }

    if (!super.match(patternNode,matchedNode,context)) return false;
    boolean result;
    PsiDeclarationStatement dcl = (PsiDeclarationStatement)patternNode;

    if (matchedNode instanceof PsiDeclarationStatement) {
      result = GlobalMatchingVisitor.continueMatchingSequentially(
        new SsrFilteringNodeIterator(patternNode.getFirstChild()),
        new SsrFilteringNodeIterator(matchedNode.getFirstChild()),
        context
      );
    } else {
      final PsiElement[] declared = dcl.getDeclaredElements();

      // declaration statement could wrap class or dcl
      if (declared.length >0 &&
          ( ( declared[0] instanceof PsiVariable && matchedNode instanceof PsiVariable) ||
            ( declared[0] instanceof PsiClass && matchedNode instanceof PsiClass)
          ) &&
          !(matchedNode.getParent() instanceof PsiDeclarationStatement) // skip twice matching for child
         ) {
        result = GlobalMatchingVisitor.continueMatchingSequentially(
          new ArrayBackedNodeIterator(declared),
          new CountingNodeIterator(
            declared.length,
            new SsrFilteringNodeIterator(matchedNode)
          ),
          context
        );

        if (result &&
            declared[0] instanceof PsiVariable && matchedNode instanceof PsiField
            ) {
          // we may have comments behind to match!
          final PsiElement[] children = dcl.getChildren();

          final PsiElement lastChild = children[children.length - 1];
          if (lastChild instanceof PsiComment) {
            final PsiElement[] fieldChildren = matchedNode.getChildren();

            result = context.getPattern().getHandler(lastChild).match(
              lastChild,
              fieldChildren[fieldChildren.length-1],
              context
            );
          }
        }
      } else {
        result = false;
      }
    }

    return result;
  }

  public boolean shouldAdvanceTheMatchFor(PsiElement patternElement, PsiElement matchedElement) {
    if (patternElement instanceof PsiComment &&
        ( matchedElement instanceof PsiField ||
          matchedElement instanceof PsiClass
        )
       ) {
      return false;
    }

    return super.shouldAdvanceTheMatchFor(patternElement,matchedElement);
  }

  public void setCommentHandler(final MatchingHandler commentHandler) {
    myCommentHandler = commentHandler;
  }
}
