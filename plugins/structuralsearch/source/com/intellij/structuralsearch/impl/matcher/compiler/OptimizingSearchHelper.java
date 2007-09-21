package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiFile;

import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
interface OptimizingSearchHelper {
  boolean doOptimizing();
  void clear();

  boolean addWordToSearchInCode(final String refname);

  boolean addWordToSearchInComments(final String refname);

  boolean addWordToSearchInLiterals(final String refname);

  void endTransaction();

  boolean addDescendantsOf(final String refname, final boolean subtype);

  boolean isScannedSomething();

  Set<PsiFile> getFilesSetToScan();
}
