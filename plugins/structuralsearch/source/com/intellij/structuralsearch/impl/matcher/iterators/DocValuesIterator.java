package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.openapi.application.ApplicationInfo;

/**
 * Iterates over java doc values tag
 */
public class DocValuesIterator extends NodeIterator {
  private PsiElement start;
  private PsiElement previous,current;
  private static final IElementType tokenType =PsiDocToken.DOC_COMMENT_DATA;

  private static final PsiElement advance(PsiElement next) {
    while(next!=null &&
          ( !(next instanceof PsiDocToken) ||
            ((PsiDocToken)next).getTokenType() != tokenType
          )
         ) {
      next = next.getNextSibling();
    }

    return next;
  }

  private final PsiElement goback(PsiElement next) {
    while(next!=null &&
          ( !(next instanceof PsiDocToken) ||
            ((PsiDocToken)next).getTokenType() != tokenType
          )
         ) {
      next = next.getPrevSibling();
    }

    return next;
  }

  public DocValuesIterator(PsiElement start) {
    this.start = start;
    current = advance(start);
  }
  public boolean hasNext() {
    return current!=null;
  }

  public PsiElement current() {
    return current;
  }

  public void advance() {
    if (current!=null) {
      previous = current;
      current = advance(current.getNextSibling());
    }
  }

  public void rewind() {
    current = previous;
    previous = goback(previous.getPrevSibling());
  }

  public void reset() {
    current = advance(start);
  }
}
