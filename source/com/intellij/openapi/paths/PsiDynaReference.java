/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.paths;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class PsiDynaReference<T extends PsiElement> extends PsiReferenceBase<T>
  implements PsiPolyVariantReference, QuickFixProvider<PsiDynaReference>, LocalQuickFixProvider, EmptyResolveMessageProvider {

  private List<PsiReference> myReferences = new ArrayList<PsiReference>();
  private int myChoosenOne = -1;
  private boolean mySoft;
  private ResolveResult[] myCachedResult;

  public PsiDynaReference(final T psiElement, boolean soft) {
    super(psiElement);
    mySoft = soft;
  }

  public void addReferences(Collection<PsiReference> references) {
    myReferences.addAll(references);
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


  public TextRange getRangeInElement() {

    PsiReference resolved = null;
    PsiReference reference = myReferences.get(0);

    if (reference.resolve() != null) {
      resolved = reference;
    }

    final TextRange range = reference.getRangeInElement();
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    for (int i = 1; i < myReferences.size(); i++) {
      reference = myReferences.get(i);
      final TextRange textRange = getRange(reference);
      start = Math.min(start, textRange.getStartOffset());
      if (resolved == null) {
        end = Math.max(end, textRange.getEndOffset());
      }
    }
    return new TextRange(start, end);
  }

  private TextRange getRange(PsiReference reference) {
    TextRange rangeInElement = reference.getRangeInElement();
    PsiElement element = reference.getElement();
    while(element != myElement) {
      rangeInElement = rangeInElement.shiftRight(element.getStartOffsetInParent());
      element = element.getParent();
      if (element instanceof PsiFile) break;
    }
    return rangeInElement;
  }

  public PsiElement resolve(){
    final ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  public String getCanonicalText(){
    final PsiReference reference = chooseReference();
    return reference == null ? myReferences.get(0).getCanonicalText() : reference.getCanonicalText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException{
    final PsiReference reference = chooseReference();
    if (reference != null) {
      return reference.handleElementRename(newElementName);
    }
    return myElement;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    for (PsiReference reference : myReferences) {
      if (reference instanceof FileReference) {
        return reference.bindToElement(element);
      }
    }
    return myElement;
  }

  public boolean isReferenceTo(PsiElement element){
    for (PsiReference reference : myReferences) {
      if (reference.isReferenceTo(element)) return true;
    }
    return false;
  }


  public Object[] getVariants() {
    switch (myReferences.size()) {
      case 0:
        return new Object[0];
      case 1:
        return myReferences.get(0).getVariants();
      default:
        int minOffset = getRangeInElement().getStartOffset();
        final String text = myElement.getText();
        List<Object> variants = new ArrayList<Object>();
        for(PsiReference ref: myReferences) {
          final int startOffset = ref.getRangeInElement().getStartOffset();
          final String prefix;
          if (startOffset > minOffset) {
            prefix = text.substring(minOffset, startOffset);
          } else {
            prefix = null;
          }
          Object[] refVariants = ref.getVariants();
          if (refVariants != null) {
            for(Object refVariant : refVariants) {
              if (prefix != null) {
                if (refVariant instanceof CandidateInfo) {
                  refVariant = ((CandidateInfo)refVariant).getElement();
                }
                final LookupItem item = LookupItemUtil.objectToLookupItem(refVariant);
                final String s = item.getLookupString();
                item.setLookupString(prefix + s);
                variants.add(item);
              } else {
                variants.add(refVariant);
              }
            }
          }
        }
        return variants.toArray();
    }
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    if (myCachedResult == null) {
      myCachedResult = innerResolve(incompleteCode);  
    }
    return myCachedResult;
  }

  protected ResolveResult[] innerResolve(final boolean incompleteCode) {
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

  @Nullable
  private PsiReference chooseReference(){
    if(myChoosenOne != -1){
      return myReferences.get(myChoosenOne);
    }
    boolean flag = false;
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
      }
    }
    return myChoosenOne >= 0 ? myReferences.get(myChoosenOne) : null;
  }

  public boolean isSoft() {
    return mySoft;
  }

  public void registerQuickfix(final HighlightInfo info, final PsiDynaReference reference) {
    for (Object ref: reference.myReferences) {
      if (ref instanceof QuickFixProvider) {
        ((QuickFixProvider)ref).registerQuickfix(info, (PsiReference)ref);
      }
    }
  }

  public String getUnresolvedMessagePattern() {
    final PsiReference reference = chooseReference();

    return reference instanceof EmptyResolveMessageProvider ?
           ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern() :
            XmlErrorMessages.message("cannot.resolve.symbol");
  }

  public LocalQuickFix[] getQuickFixes() {
    final ArrayList<LocalQuickFix> list = new ArrayList<LocalQuickFix>();
    for (Object ref: myReferences) {
      if (ref instanceof LocalQuickFixProvider) {
        list.addAll(Arrays.asList(((LocalQuickFixProvider)ref).getQuickFixes()));
      }
    }
    return list.toArray(new LocalQuickFix[list.size()]);
  }
}
