package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.openapi.application.ApplicationInfo;

import java.util.ArrayList;

/**
 * Iterates over java doc values tag
 */
public class DocValuesIterator extends NodeIterator {
  private int index;
  private ArrayList<PsiElement> tokens = new ArrayList<PsiElement>(2);
  private static final IElementType tokenType =PsiDocToken.DOC_COMMENT_DATA;

  public DocValuesIterator(PsiElement start) {
    for(PsiElement e = start; e != null; e = e.getNextSibling()) {
      if (e instanceof PsiDocTagValue) tokens.add(e);
      else if (e instanceof PsiDocToken && ((PsiDocToken)e).getTokenType() == tokenType) {
        tokens.add(e);
        e = advanceToNext(e);
      }
    }
  }

  // since doctag value may be inside doc comment we specially skip that nodes from list
  static PsiElement advanceToNext(PsiElement e) {
    PsiElement nextSibling = e.getNextSibling();
    if (nextSibling instanceof PsiDocTagValue) e = nextSibling;

    nextSibling = e.getNextSibling();

    if (nextSibling instanceof PsiDocToken &&
        ((PsiDocToken)nextSibling).getTokenType() == tokenType
       ) {
      e = nextSibling;
    }
    return e;
  }

  public boolean hasNext() {
    return index >=0 && index < tokens.size();
  }

  public PsiElement current() {
    return hasNext() ? tokens.get(index) : null;
  }

  public void advance() {
    if (index < tokens.size()) ++ index;
  }

  public void rewind() {
    if (index >= 0) --index;
  }

  public void reset() {
    index = 0;
  }
}
