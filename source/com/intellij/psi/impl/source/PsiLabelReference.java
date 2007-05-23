package com.intellij.psi.impl.source;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.05.2003
 * Time: 23:32:45
 * To change this template use Options | File Templates.
 */
public class PsiLabelReference implements PsiReference{
  private final PsiStatement myStatement;
  private PsiIdentifier myIdentifier;

  public PsiLabelReference(PsiStatement stat, PsiIdentifier identifier){
    myStatement = stat;
    myIdentifier = identifier;
  }

  public PsiElement getElement(){
    return myStatement;
  }

  public TextRange getRangeInElement(){
    final int parent = myIdentifier.getStartOffsetInParent();
    return new TextRange(parent, myIdentifier.getTextLength() + parent);
  }

    public PsiElement resolve(){
      final String label = myIdentifier.getText();
      if(label == null) return null;
      PsiElement context = myStatement;
      while(context != null){
        if(context instanceof PsiLabeledStatement){
          final PsiLabeledStatement statement = (PsiLabeledStatement) context;
          if(label.equals(statement.getName()))
            return statement;
        }
        context = context.getContext();
      }
      return null;
    }

    public String getCanonicalText(){
      return getElement().getText();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException{
      myIdentifier = (PsiIdentifier) SharedPsiElementImplUtil.setName(myIdentifier, newElementName);
      return myIdentifier;
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException{
      if(element instanceof PsiLabeledStatement){
        myIdentifier = (PsiIdentifier) SharedPsiElementImplUtil.setName(myIdentifier, ((PsiLabeledStatement)element).getName());
        return myIdentifier;
      }
      throw new IncorrectOperationException("Can't bind not to labeled statement");
    }

    public boolean isReferenceTo(PsiElement element){
      return resolve() == element;
    }

    public Object[] getVariants(){
      final List result = new ArrayList();
      PsiElement context = myStatement;
      while(context != null){
        if(context instanceof PsiLabeledStatement){
          result.add(context);
        }
        context = context.getContext();
      }
      return result.toArray();
    }

  public boolean isSoft(){
    return false;
  }
}
