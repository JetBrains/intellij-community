package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

public class PsiKeywordImpl extends LeafPsiElement implements PsiKeyword, PsiJavaToken {
  public PsiElement[] create(Class parentClass, PsiElementFactory factory, String prefix)
  throws IncorrectOperationException{
    List<PsiKeyword> ret = new ArrayList<PsiKeyword>();
    if(parentClass.isAssignableFrom(PsiClass.class)){
      if(PsiKeyword.CLASS.startsWith(prefix)){
        ret.add(factory.createKeyword(PsiKeyword.CLASS));
      }
    }

    return (PsiElement[])ret.toArray(new PsiElement[ret.size()]);
  }

  public PsiKeywordImpl(IElementType type, CharSequence buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
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
