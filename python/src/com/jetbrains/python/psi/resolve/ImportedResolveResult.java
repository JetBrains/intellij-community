package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author yole
 */
public class ImportedResolveResult extends RatedResolveResult {
  private final List<PsiElement> myNameDefiners;

  public ImportedResolveResult(PsiElement element, int rate, List<PsiElement> nameDefiners) {
    super(rate, element);
    myNameDefiners = nameDefiners;
  }

  public List<PsiElement> getNameDefiners() {
    return myNameDefiners;
  }

  @Override
  public RatedResolveResult replace(PsiElement what) {
    return new ImportedResolveResult(what, getRate(), myNameDefiners);
  }
}
