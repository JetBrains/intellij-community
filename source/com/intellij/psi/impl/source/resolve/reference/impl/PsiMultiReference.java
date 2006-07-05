package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 02.04.2003
 * Time: 12:22:03
 * To change this template use Options | File Templates.
 */

public class PsiMultiReference implements PsiPolyVariantReference {
  private PsiReference[] myReferences;
  private final PsiElement myElement;

  private int myChoosenOne = -1;

  public PsiMultiReference(@NotNull PsiReference[] references, PsiElement element){
    myReferences = references;
    myElement = element;
  }

  public PsiReference[] getReferences() {
    return myReferences;
  }

  public void addReference(PsiReference reference) {
    myReferences = ArrayUtil.append(myReferences, reference);
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



  private PsiReference chooseReference(){
    if(myChoosenOne != -1){
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

  public boolean isSoft(){
    return false;
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiReference[] refs = getReferences();
    List<ResolveResult> result = new ArrayList<ResolveResult>();
    for (PsiReference reference : refs) {
      if (reference instanceof PsiPolyVariantReference) {
        result.addAll(Arrays.asList(((PsiPolyVariantReference)reference).multiResolve(incompleteCode)));
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

}
