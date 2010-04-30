package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.NameDefiner;

import java.util.List;

/**
 * @author yole
 */
public class ImportedResolveResult extends RatedResolveResult {
  private final List<NameDefiner> myNameDefiners;

  public ImportedResolveResult(PsiElement element, int rate, List<NameDefiner> nameDefiners) {
    super(rate, element);
    myNameDefiners = nameDefiners;
  }

  public List<NameDefiner> getNameDefiners() {
    return myNameDefiners;
  }

  @Override
  public RatedResolveResult replace(PsiElement what) {
    return new ImportedResolveResult(what, getRate(), myNameDefiners);
  }
}
