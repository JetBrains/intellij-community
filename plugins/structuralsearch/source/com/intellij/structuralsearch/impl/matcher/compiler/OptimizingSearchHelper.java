package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiFile;

import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
public interface OptimizingSearchHelper {
  boolean doOptimizing();
  void clear();

  boolean addWordToSearchInCode(final String refname);

  boolean addWordToSearchInText(final String refname);

  boolean addWordToSearchInComments(final String refname);

  boolean addWordToSearchInLiterals(final String refname);

  void endTransaction();

  boolean isScannedSomething();

  Set<PsiFile> getFilesSetToScan();
}
