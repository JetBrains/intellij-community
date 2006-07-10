/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.javaee.web;

import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class PsiDynaReference<T extends PsiElement> extends PsiReferenceBase<T> implements PsiPolyVariantReference {

  private List<PsiReference> myReferences = new ArrayList<PsiReference>();
  private int myChoosenOne;
  private boolean mySoft;

  public PsiDynaReference(final T psiElement, boolean soft) {
    super(psiElement);
    mySoft = soft;
  }

  public void addReference(PsiReference reference) {
    myReferences.add(reference);
  }

  public TextRange getMinimumRangeInElement() {
    int start = 0;
    int end = Integer.MAX_VALUE;
    for (PsiReference ref: myReferences) {
      final TextRange range = ref.getRangeInElement();
      start = Math.max(start, range.getStartOffset());
      end = Math.min(end, range.getEndOffset());
    }
    return new TextRange(start, end);
  }


  public TextRange getRangeInElement(){
    final PsiReference chosenRef = chooseReference();
    TextRange rangeInElement = chosenRef.getRangeInElement();
    PsiElement element = chosenRef.getElement();
    while(element != myElement) {
      rangeInElement = rangeInElement.shiftRight(element.getStartOffsetInParent());
      element = element.getParent();
      if (element instanceof PsiFile) break;
    }
    return rangeInElement;
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
    for (PsiReference reference : myReferences) {
      if (reference.isReferenceTo(element)) return true;
    }
    return false;
  }


  public Object[] getVariants() {
    Set<Object> variants = new HashSet<Object>();
    for(PsiReference ref: myReferences) {
      Object[] refVariants = ref.getVariants();
      for(Object refVariant : refVariants) {
        variants.add(refVariant);
      }
    }
    return variants.toArray();
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    List<ResolveResult> result = new ArrayList<ResolveResult>();
    for (PsiReference reference : myReferences) {
      if (reference instanceof PsiPolyVariantReference) {
        for (ResolveResult rr: ((PsiPolyVariantReference)reference).multiResolve(incompleteCode)) {
          if (rr.isValidResult()) {
            result.add(rr);
          }
        }
      }
      else {
        final PsiElement resolved = reference.resolve();
        if (resolved != null) {
          result.add(new PsiElementResolveResult(resolved));
        }
      }
    }

    return result.toArray(new ResolveResult[result.size()]);
  }

  private PsiReference chooseReference(){
    if(myChoosenOne != -1){
      return myReferences.get(myChoosenOne);
    }
    boolean flag = false;
    myChoosenOne = 0;
    boolean strict = false;
    for(int i = 0; i < myReferences.size(); i++){
      final PsiReference reference = myReferences.get(i);
      if(reference.isSoft() && flag) continue;
      if(!reference.isSoft() && !flag){
        myChoosenOne = i;
        flag = true;
        continue;
      }
      if(reference.resolve() != null){
        myChoosenOne = i;
        strict = true;
      }
      if(!strict){
        // One reference inside other
        final TextRange rangeInElement1 = reference.getRangeInElement();
        final TextRange rangeInElement2 = myReferences.get(myChoosenOne).getRangeInElement();
        if(rangeInElement1.getStartOffset() >= rangeInElement2.getStartOffset()
           && rangeInElement1.getEndOffset() <= rangeInElement2.getEndOffset()){
          myChoosenOne = i;
        }
      }
    }
    return myReferences.get(myChoosenOne);
  }

  public boolean isSoft() {
    return mySoft;
  }
}
