package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 02.04.2003
 * Time: 12:22:03
 * To change this template use Options | File Templates.
 */

public class PsiMultiReference implements PsiReference{
  private final PsiReference[] myReferences;
  private final PsiElement myElement;

  public PsiMultiReference(PsiReference[] references, PsiElement element){
    myReferences = references;
    myElement = element;
  }

  private int myChoosenOne = -1;

  private PsiReference chooseReference(){
    if(myChoosenOne > 0){
      return myReferences[myChoosenOne];
    }
    boolean flag = false;
    myChoosenOne = 0;
    boolean strict = false;
    for(int i = 0; i < myReferences.length; i++){
      final PsiReference reference = myReferences[i];
      if(reference.isSoft() && flag) continue;
      if(!reference.isSoft() && !flag){
        myChoosenOne = i;
        flag = true;
        continue;
      }
      if(reference instanceof GenericReference){
        if(((GenericReference)reference).getContext() != null){
          myChoosenOne = i;
          strict = true;
        }
      }
      if(reference.resolve() != null){
        myChoosenOne = i;
        strict = true;
      }
      if(!strict){
        // One reference inside other
        final TextRange rangeInElement1 = reference.getRangeInElement();
        final TextRange rangeInElement2 = myReferences[myChoosenOne].getRangeInElement();
        if(rangeInElement1.getStartOffset() >= rangeInElement2.getStartOffset()
           && rangeInElement1.getEndOffset() <= rangeInElement2.getEndOffset()){
          myChoosenOne = i;
        }
      }
    }
    return myReferences[myChoosenOne];
  }

  public PsiElement getElement(){
    return myElement;
  }

  public TextRange getRangeInElement(){
    return chooseReference().getRangeInElement();
  }

  public PsiElement resolve(){
    return chooseReference().resolve();
  }

  public String getCanonicalText(){
    return chooseReference().getCanonicalText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException{
    return chooseReference().handleElementRename(newElementName);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException{
    return chooseReference().bindToElement(element);
  }

  public boolean isReferenceTo(PsiElement element){
    return chooseReference().isReferenceTo(element);
  }

  public Object[] getVariants(){
    return chooseReference().getVariants();
  }

  public boolean isSoft(){
    return false;
  }

  public void processVariants(final PsiScopeProcessor processor, PsiSubstitutor substitutor){
    if(chooseReference() instanceof GenericReference)
      ((GenericReference)chooseReference()).processVariants(processor);
  }
}
