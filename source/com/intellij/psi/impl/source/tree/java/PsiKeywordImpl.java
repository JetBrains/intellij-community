package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.CharTable;

import java.util.ArrayList;
import java.util.List;

public class PsiKeywordImpl extends LeafPsiElement implements PsiKeyword, PsiJavaToken {
  public PsiElement[] create(Class parentClass, PsiElementFactory factory, String prefix)
  throws IncorrectOperationException{
    List ret = new ArrayList();
    if(parentClass.isAssignableFrom(PsiClass.class)){
      if("class".startsWith(prefix)){
        ret.add(factory.createKeyword("class"));
      }
    }

    return (PsiElement[])ret.toArray(new PsiElement[ret.size()]);
  }

  public PsiKeywordImpl(IElementType type, char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(type, buffer, startOffset, endOffset, lexerState, table);
  }

  public IElementType getTokenType(){
    return getElementType();
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitKeyword(this);
  }

  public String toString(){
    return "PsiKeyword:" + getText();
  }
}
