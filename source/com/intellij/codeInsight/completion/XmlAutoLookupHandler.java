package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlTag;

/**
 *
 */
public class XmlAutoLookupHandler extends CodeCompletionHandler{
  protected boolean isAutocompleteOnInvocation() {
    return false;
  }

  protected boolean isAutocompleteCommonPrefixOnInvocation() {
    return false;
  }

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData){
  }

  protected LookupData getLookupData(CompletionContext context){
    PsiFile file = context.file;
    int offset = context.startOffset;

    PsiElement lastElement = file.findElementAt(offset - 1);
    if (lastElement == null) return LookupData.EMPTY;

    if (lastElement.getText().equals("<")) {
      return super.getLookupData(context);
    }
    //if (lastElement instanceof PsiWhiteSpace && lastElement.getPrevSibling() instanceof XmlTag) {
    //  return super.getLookupData(context);
    //}

    return LookupData.EMPTY;
  }
}
